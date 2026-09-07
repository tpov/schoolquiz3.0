"use strict";

// Private, permanent token-level serialization fence. A signal is NOT proof of a refund:
// PENDING blocks new credit with retryable unavailability; only a corroborated cancellation
// becomes CONFIRMED. No balance, owner, SKU configuration or price is accepted from the signal.
const {
  MAX_TOKEN_LENGTH, SKU_PATTERN, PLAY_OUTCOME_FOUND, PLAY_OUTCOME_NOT_FOUND,
  PURCHASE_STATE_CANCELED, STATUS_CREDITED, SETTLEMENT_REFUSE, REASON_PURCHASE_CANCELED,
  settlementGate, accountIdFor,
} = require("./purchase-verification");
const PURCHASE_REVOCATIONS_COLLECTION = "purchase_revocations";
const REVOCATION_PENDING = "PENDING_VERIFICATION";
const REVOCATION_CONFIRMED = "CONFIRMED_REVOKED";
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
  const canceled = notification.oneTimeProductNotification;
  if (object(canceled) && canceled.notificationType === 2 && token(canceled.purchaseToken) &&
      typeof canceled.sku === "string" && SKU_PATTERN.test(canceled.sku)) {
    return {kind: "ONE_TIME_PENDING_CANCELED", orderId: "", sku: canceled.sku, eventTimeMs};
  }
  return null; // Partial refunds, subscriptions and unsupported/malformed payloads are not full voids.
}

function readRevocationFence(raw, {id, packageName}) {
  if (!raw) return null;
  if (!object(raw) || raw.schemaVersion !== 1 || raw.settlementId !== id || !text(raw.packageName) ||
      (packageName && raw.packageName !== packageName) ||
      ![REVOCATION_PENDING, REVOCATION_CONFIRMED].includes(raw.state) ||
      !time(raw.createdAtMs) || !text(raw.firstNotificationId) || !object(raw.signal) ||
      !["ONE_TIME_FULL_VOID", "ONE_TIME_PENDING_CANCELED"].includes(raw.signal.kind) ||
      !time(raw.signal.eventTimeMs) ||
      (raw.state === REVOCATION_CONFIRMED && (!time(raw.confirmedAtMs) || !text(raw.confirmationNotificationId) ||
        !["PLAY_TOKEN_NOT_FOUND", "PLAY_V1_CANCELED", "PLAY_V2_CANCELLED", "EXISTING_REFUNDED_SETTLEMENT"].includes(raw.proof)))) {
    throw new Error("Purchase revocation fence requires repair");
  }
  return raw;
}
function pendingRevocation({id, packageName, messageId, signal, now}) {
  if (!time(now)) throw new Error("Invalid trusted revocation clock");
  return {schemaVersion: 1, settlementId: id, packageName, state: REVOCATION_PENDING,
    firstNotificationId: messageId, signal, createdAtMs: now};
}
function confirmedRevocation(fence, {now, messageId, proof}) {
  if (!time(now)) throw new Error("Invalid trusted revocation clock");
  if (fence.state === REVOCATION_CONFIRMED) return fence;
  return {...fence, state: REVOCATION_CONFIRMED, confirmedAtMs: Math.max(now, fence.createdAtMs),
    confirmationNotificationId: messageId, proof};
}
function revocationSettlementGate({fence, existing, uid}) {
  const prior = settlementGate(existing, uid);
  if (prior?.decision === SETTLEMENT_REFUSE) return prior; // Owner check remains first.
  if (!fence) return prior;
  if (fence.state === REVOCATION_PENDING) return {decision: SETTLEMENT_REVOCATION_PENDING};
  return {decision: SETTLEMENT_REFUSE, code: "failed-precondition", reasonCode: REASON_PURCHASE_CANCELED,
    reason: "Play confirmed cancellation of this purchase"};
}

/** V2 is used only to resolve ownerless, SKU-less RTDNs, never to decide grant amounts. */
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

module.exports = {PURCHASE_REVOCATIONS_COLLECTION, REVOCATION_PENDING, REVOCATION_CONFIRMED,
  SETTLEMENT_REVOCATION_PENDING, revocationSignal, readRevocationFence, pendingRevocation,
  confirmedRevocation, revocationSettlementGate, cancellationProofFromV2, cancellationProofFromV1};
