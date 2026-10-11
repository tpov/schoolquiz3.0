"use strict";

// Private, permanent token-level serialization fence. A signal is NOT proof of a refund:
// PENDING blocks new credit with retryable unavailability; only a corroborated cancellation
// becomes CONFIRMED. No balance, owner, SKU configuration or price is accepted from the signal.
const {
  MAX_TOKEN_LENGTH, SKU_PATTERN, PLAY_OUTCOME_FOUND, PLAY_OUTCOME_NOT_FOUND,
  PURCHASE_STATE_CANCELED, STATUS_CREDITED, SETTLEMENT_REFUSE, REASON_PURCHASE_CANCELED,
  settlementGate, accountIdFor,
} = require("./purchase-verification");
const {readProductPurchaseV2} = require("./play-developer-api");
const PURCHASE_REVOCATIONS_COLLECTION = "purchase_revocations";
const REVOCATION_PENDING = "PENDING_VERIFICATION";
const REVOCATION_CONFIRMED = "CONFIRMED_REVOKED";
// A partial void is deliberately not terminal for the token: the remaining units may still be
// credited/consumed. It carries the last Play V2 observation that settlement and the next RTDN
// must agree with.
const REVOCATION_PARTIAL = "PARTIAL_RECONCILED";
const SETTLEMENT_REVOCATION_PENDING = "revocation_pending";
const object = (value) => value !== null && typeof value === "object" && !Array.isArray(value);
const token = (value) => typeof value === "string" && value.length > 0 && value.length <= MAX_TOKEN_LENGTH;
const text = (value) => typeof value === "string" && value.length > 0 && value.length <= 512;
const time = (value) => Number.isSafeInteger(value) && value >= 0;

/** Called ONLY by the trusted expected Play topic handler after exact package verification. */
function revocationSignal(notification, packageName) {
  if (!object(notification) || !text(packageName) || notification.packageName !== packageName) return null;
  const variants = ["voidedPurchaseNotification", "oneTimeProductNotification", "subscriptionNotification",
    "testNotification", "pendingRefundReviewNotification"].filter((key) => notification[key] !== undefined);
  if (variants.length !== 1) return null;
  const eventTimeMs = typeof notification.eventTimeMillis === "string" && /^\d+$/.test(notification.eventTimeMillis) ?
    Number(notification.eventTimeMillis) : notification.eventTimeMillis;
  if (!time(eventTimeMs)) return null;
  const voided = notification.voidedPurchaseNotification;
  if (object(voided) && voided.productType === 2 && voided.refundType === 1 &&
      token(voided.purchaseToken) && text(voided.orderId)) {
    return {kind: "ONE_TIME_FULL_VOID", orderId: voided.orderId, sku: "", eventTimeMs};
  }
  if (object(voided) && voided.productType === 2 && voided.refundType === 2 &&
      token(voided.purchaseToken) && text(voided.orderId)) {
    return {kind: "ONE_TIME_PARTIAL_VOID", orderId: voided.orderId, sku: "", eventTimeMs};
  }
  // A subscription void (productType 1) is how Play reports a refunded premium purchase. It is
  // fenced and recorded, but no time is revoked here: which Play state corroborates a subscription
  // refund is its own decision (deferred-work). Dropping the message instead would leave refunded
  // premium running and the token re-creditable.
  if (object(voided) && voided.productType === 1 && [1, 2].includes(voided.refundType) &&
      token(voided.purchaseToken) && text(voided.orderId)) {
    return {kind: "SUBSCRIPTION_VOID", orderId: voided.orderId, sku: "", eventTimeMs};
  }
  const canceled = notification.oneTimeProductNotification;
  if (object(canceled) && canceled.notificationType === 2 && token(canceled.purchaseToken) &&
      typeof canceled.sku === "string" && SKU_PATTERN.test(canceled.sku)) {
    return {kind: "ONE_TIME_PENDING_CANCELED", orderId: "", sku: canceled.sku, eventTimeMs};
  }
  return null; // Subscriptions and malformed/unknown payloads are outside this one-time model.
}

function readRevocationFence(raw, {id, packageName}) {
  if (!raw) return null;
  if (!object(raw)) throw new Error("Purchase revocation fence requires repair");
  const schemaVersion = raw.schemaVersion;
  const hasPartial = raw.partial !== undefined;
  if (![1, 2].includes(schemaVersion) || raw.settlementId !== id || !text(raw.packageName) ||
      (packageName && raw.packageName !== packageName) ||
      ![REVOCATION_PENDING, REVOCATION_CONFIRMED, REVOCATION_PARTIAL].includes(raw.state) ||
      !time(raw.createdAtMs) || !text(raw.firstNotificationId) || !object(raw.signal) ||
      !["ONE_TIME_FULL_VOID", "ONE_TIME_PARTIAL_VOID", "ONE_TIME_PENDING_CANCELED", "SUBSCRIPTION_VOID"].includes(raw.signal.kind) ||
      !time(raw.signal.eventTimeMs) ||
      (hasPartial && (schemaVersion !== 2 || !partialObservation(raw.partial))) ||
      (raw.state === REVOCATION_PARTIAL &&
        (schemaVersion !== 2 || raw.signal.kind !== "ONE_TIME_PARTIAL_VOID" || !partialObservation(raw.partial))) ||
      (raw.state === REVOCATION_CONFIRMED && (!time(raw.confirmedAtMs) || !text(raw.confirmationNotificationId) ||
        !["PLAY_TOKEN_NOT_FOUND", "PLAY_V1_CANCELED", "PLAY_V2_CANCELLED", "EXISTING_REFUNDED_SETTLEMENT"].includes(raw.proof)))) {
    throw new Error("Purchase revocation fence requires repair");
  }
  return raw;
}

function partialObservation(raw) {
  if (!object(raw) || !text(raw.sku) || !text(raw.orderId) ||
      !Number.isSafeInteger(raw.purchaseQuantity) || raw.purchaseQuantity < 2 ||
      !Number.isSafeInteger(raw.refundableQuantity) || raw.refundableQuantity < 1 ||
      raw.refundableQuantity >= raw.purchaseQuantity ||
      !Number.isSafeInteger(raw.refundedQuantity) || raw.refundedQuantity < 1 ||
      raw.refundedQuantity !== raw.purchaseQuantity - raw.refundableQuantity ||
      !time(raw.observedAtMs) || !text(raw.notificationId) ||
      (raw.obfuscatedExternalAccountId !== "" && !text(raw.obfuscatedExternalAccountId))) return false;
  return true;
}

function pendingRevocation({id, packageName, messageId, signal, now, previous = null}) {
  if (!time(now)) throw new Error("Invalid trusted revocation clock");
  const carryPartial = previous && previous.partial ? previous.partial : undefined;
  return {
    schemaVersion: signal.kind === "ONE_TIME_PARTIAL_VOID" || carryPartial || previous?.schemaVersion === 2 ? 2 : 1,
    settlementId: id,
    packageName,
    state: REVOCATION_PENDING,
    firstNotificationId: previous ? previous.firstNotificationId : messageId,
    signal,
    createdAtMs: previous ? previous.createdAtMs : now,
    ...(carryPartial ? {partial: carryPartial} : {}),
  };
}
function confirmedRevocation(fence, {now, messageId, proof}) {
  if (!time(now)) throw new Error("Invalid trusted revocation clock");
  if (fence.state === REVOCATION_CONFIRMED) return fence;
  return {...fence, state: REVOCATION_CONFIRMED, confirmedAtMs: Math.max(now, fence.createdAtMs),
    confirmationNotificationId: messageId, proof};
}

function partiallyConfirmedRevocation(fence, {now, messageId, observation}) {
  if (!time(now) || !partialObservation(observation)) throw new Error("Invalid partial refund verification");
  if (fence.state === REVOCATION_CONFIRMED) throw new Error("Fully revoked purchase cannot become partial");
  if (fence.signal.kind !== "ONE_TIME_PARTIAL_VOID") {
    throw new Error("Partial refund proof does not match its token fence");
  }
  const prior = fence.partial;
  if (prior && (prior.purchaseQuantity !== observation.purchaseQuantity ||
      observation.refundableQuantity > prior.refundableQuantity ||
      observation.refundedQuantity < prior.refundedQuantity)) {
    throw new Error("Partial refund quantity regressed; redeliver for repair");
  }
  return {...fence, schemaVersion: 2, state: REVOCATION_PARTIAL, partial: observation};
}
function revocationSettlementGate({fence, existing, uid}) {
  const prior = settlementGate(existing, uid);
  if (prior?.decision === SETTLEMENT_REFUSE) return prior; // Owner check remains first.
  if (!fence) return prior;
  if (fence.state === REVOCATION_PENDING) return {decision: SETTLEMENT_REVOCATION_PENDING};
  if (fence.state === REVOCATION_CONFIRMED) {
    return {decision: SETTLEMENT_REFUSE, code: "failed-precondition", reasonCode: REASON_PURCHASE_CANCELED,
      reason: "Play confirmed cancellation of this purchase"};
  }
  return prior;
}

/**
 * Reads the V2 quantity evidence required before a new V1 multi-quantity gold grant.  Both
 * resources must name exactly the same immutable purchase; V2 is not a fallback for V1.
 * `refundableQuantity === 0` is deliberately returned as a confirmed fact so the caller can
 * refuse a fully refunded purchase without ever creating a grant.
 */
function multiQuantityGrantProofFromV2(lookup, purchase) {
  if (lookup?.outcome !== PLAY_OUTCOME_FOUND || !purchase || !Number.isSafeInteger(purchase.quantity) ||
      purchase.quantity < 2) return {confirmed: false};
  const v2 = readProductPurchaseV2(lookup.purchase);
  if (!v2 || v2.purchaseState !== "PURCHASED" || v2.productId !== purchase.productId ||
      v2.orderId !== purchase.orderId || v2.obfuscatedExternalAccountId !== purchase.obfuscatedExternalAccountId ||
      v2.quantity !== purchase.quantity || v2.refundableQuantity > v2.quantity) {
    return {confirmed: false};
  }
  return {
    confirmed: true,
    sku: v2.productId,
    orderId: v2.orderId,
    obfuscatedExternalAccountId: v2.obfuscatedExternalAccountId,
    purchaseQuantity: v2.quantity,
    refundableQuantity: v2.refundableQuantity,
  };
}

/**
 * Chooses the only safe quantity for a new grant.  A just-read V2 proof is mandatory for new
 * multi-quantity gold, while a durable partial RTDN fence can only lower (never raise) it.
 */
function partialRefundGrantAdjustment({fence, purchase, uid, grantProof = null}) {
  if (!purchase || !Number.isSafeInteger(purchase.quantity) || purchase.quantity < 1) return {valid: false};
  let quantity = purchase.quantity;
  if (grantProof) {
    if (!grantProofMatchesPurchase(grantProof, purchase) || grantProof.refundableQuantity < 1) return {valid: false};
    quantity = grantProof.refundableQuantity;
  }

  if (fence && fence.state === REVOCATION_PARTIAL) {
    const partial = fence.partial;
    if (!partialObservation(partial) || purchase.quantity !== partial.purchaseQuantity ||
        purchase.productId !== partial.sku || purchase.orderId !== partial.orderId ||
        (partial.obfuscatedExternalAccountId && partial.obfuscatedExternalAccountId !== accountIdFor(uid))) {
      return {valid: false};
    }
    // The transaction fence is the freshest durable observation.  It can only lower the value
    // from an earlier external V2 read; taking the minimum never restores already-refunded units
    // even if the V2 response is stale or the RTDN arrived in the read→transaction window.
    quantity = Math.min(quantity, partial.refundableQuantity);
  }

  if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > purchase.quantity) return {valid: false};
  return {
    valid: true,
    quantity,
    partial: quantity < purchase.quantity ? {
      refundedQuantity: purchase.quantity - quantity,
      refundableQuantity: quantity,
    } : null,
  };
}

function grantProofMatchesPurchase(proof, purchase) {
  return proof && proof.sku === purchase.productId && proof.orderId === purchase.orderId &&
    proof.obfuscatedExternalAccountId === purchase.obfuscatedExternalAccountId &&
    proof.purchaseQuantity === purchase.quantity && Number.isSafeInteger(proof.refundableQuantity) &&
    proof.refundableQuantity >= 0 && proof.refundableQuantity <= proof.purchaseQuantity;
}

/** Full cancellation uses V2 only to resolve ownerless, SKU-less RTDNs. */
function cancellationProofFromV2(lookup, signal) {
  if (lookup?.outcome === PLAY_OUTCOME_NOT_FOUND) return {confirmed: true, proof: "PLAY_TOKEN_NOT_FOUND"};
  if (lookup?.outcome !== PLAY_OUTCOME_FOUND || !object(lookup.purchase)) return {confirmed: false};
  const purchase = lookup.purchase;
  const items = purchase.productLineItem;
  if (!Array.isArray(items) || items.length !== 1 || !text(items[0]?.productId) ||
      (signal.sku && items[0].productId !== signal.sku) ||
      (signal.orderId && purchase.orderId !== signal.orderId)) return {confirmed: false};
  return purchase.purchaseStateContext?.purchaseState === "CANCELLED" ?
    {confirmed: true, proof: "PLAY_V2_CANCELLED"} : {confirmed: false};
}
function cancellationProofFromV1(purchase, settlement, signal) {
  if (settlement.status !== STATUS_CREDITED || (signal.sku && signal.sku !== settlement.sku) ||
      (signal.orderId && signal.orderId !== settlement.orderId)) return {confirmed: false};
  if (!purchase) return {confirmed: true, proof: "PLAY_TOKEN_NOT_FOUND"};
  if (purchase.productId !== settlement.sku ||
      (purchase.orderId && settlement.orderId && purchase.orderId !== settlement.orderId) ||
      (purchase.obfuscatedExternalAccountId && purchase.obfuscatedExternalAccountId !== accountIdFor(settlement.uid))) {
    return {confirmed: false};
  }
  return purchase.purchaseState === PURCHASE_STATE_CANCELED ?
    {confirmed: true, proof: "PLAY_V1_CANCELED"} : {confirmed: false};
}

/**
 * Validates a quantity-based partial void against ProductPurchaseV2. RTDN itself intentionally
 * does not contain a quantity; no amount is ever inferred from it. The V2 resource is required
 * to have exactly one line item because this product model credits one configured SKU per token.
 */
function partialRefundProofFromV2(lookup, settlement, signal) {
  if (lookup?.outcome !== PLAY_OUTCOME_FOUND) return {confirmed: false};
  const purchase = readProductPurchaseV2(lookup.purchase);
  // `refundableQuantity` is a monetary fact only for an active, purchased V2 line.  PENDING,
  // CANCELLED, absent and unknown states must not be reinterpreted as a partial debit; a final
  // cancellation follows the independent full-refund path.
  if (!purchase || purchase.purchaseState !== "PURCHASED" || purchase.quantity < 2 || purchase.refundableQuantity < 1 ||
      purchase.refundableQuantity >= purchase.quantity || purchase.orderId !== signal.orderId) {
    return {confirmed: false};
  }
  if (settlement) {
    const expectedQuantity = Number.isSafeInteger(settlement.purchaseQuantity) ?
      settlement.purchaseQuantity : settlement.quantity;
    if (!Number.isSafeInteger(expectedQuantity) || expectedQuantity !== purchase.quantity ||
        settlement.sku !== purchase.productId || settlement.orderId !== purchase.orderId ||
        (purchase.obfuscatedExternalAccountId &&
          purchase.obfuscatedExternalAccountId !== accountIdFor(settlement.uid))) {
      return {confirmed: false};
    }
  }
  return {
    confirmed: true,
    observation: {
      sku: purchase.productId,
      orderId: purchase.orderId,
      purchaseQuantity: purchase.quantity,
      refundableQuantity: purchase.refundableQuantity,
      refundedQuantity: purchase.quantity - purchase.refundableQuantity,
      observedAtMs: 0, // replaced with trusted server time before persistence
      notificationId: "", // replaced with the trusted Pub/Sub id before persistence
      obfuscatedExternalAccountId: purchase.obfuscatedExternalAccountId,
    },
  };
}

module.exports = {PURCHASE_REVOCATIONS_COLLECTION, REVOCATION_PENDING, REVOCATION_CONFIRMED, REVOCATION_PARTIAL,
  SETTLEMENT_REVOCATION_PENDING, revocationSignal, readRevocationFence, pendingRevocation,
  confirmedRevocation, partiallyConfirmedRevocation, revocationSettlementGate, partialRefundGrantAdjustment,
  multiQuantityGrantProofFromV2, cancellationProofFromV2, cancellationProofFromV1, partialRefundProofFromV2};
