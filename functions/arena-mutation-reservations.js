"use strict";

// A mutation receipt is immutable.  Long-running Arena work therefore reserves the same
// (uid, mutationId) in a private document until it can atomically create its final receipt.
// This module is deliberately shared by every Arena mutation so a pending season request cannot
// be overwritten by an unrelated rating or placement that happens to reuse its mutationId.

const {createHash} = require("node:crypto");
const MAX_MUTATION_ID_BYTES = 256;

class ArenaMutationReservationError extends Error {
  constructor(code, message) { super(message); this.name = "ArenaMutationReservationError"; this.code = code; }
}

function hash(value) { return createHash("sha256").update(JSON.stringify(value)).digest("hex"); }
function mutationDocumentId(uid, mutationId) { return hash([uid, mutationId]); }
function data(snapshot) { return snapshot && snapshot.exists ? snapshot.data() || {} : null; }
function text(value, name) {
  if (typeof value !== "string" || !value.trim()) throw new ArenaMutationReservationError("INVALID_ARGUMENT", `${name} is required`);
  return value;
}
function id(value, name) {
  text(value, name);
  if (value.includes("/") || value === "." || value === "..") {
    throw new ArenaMutationReservationError("INVALID_ARGUMENT", `${name} is not a document ID`);
  }
  return value;
}
function integer(value, name, min = 0) {
  if (!Number.isSafeInteger(value) || value < min) {
    throw new ArenaMutationReservationError("INVALID_ARGUMENT", `${name} must be a safe integer >= ${min}`);
  }
  return value;
}
function assertMutationIdForWrite(value) {
  text(value, "mutationId");
  if (Buffer.byteLength(value) > MAX_MUTATION_ID_BYTES) {
    throw new ArenaMutationReservationError("INVALID_ARGUMENT", `mutationId must be at most ${MAX_MUTATION_ID_BYTES} bytes`);
  }
  return value;
}

function createMutationRequest({db, uid, mutationId, operation, payload}) {
  if (!db || typeof db.doc !== "function") throw new TypeError("Firestore db required");
  id(uid, "authenticated uid"); text(mutationId, "mutationId"); text(operation, "operation");
  const documentId = mutationDocumentId(uid, mutationId);
  return {
    uid, mutationId, operation, payload, documentId,
    payloadHash: hash([operation, payload]),
    requestDigest: hash([uid, mutationId, operation, payload]),
    ref: db.doc(`arena_mutations/${documentId}`),
    reservationRef: db.doc(`arena_mutation_reservations/${documentId}`),
  };
}

function verifyReceipt(receipt, request) {
  if (receipt.uid !== request.uid || receipt.payloadHash !== request.payloadHash || receipt.operation !== request.operation) {
    throw new ArenaMutationReservationError("MUTATION_REUSE", "mutationId already has another immutable request");
  }
  return receipt;
}

function validateReservationBinding(reservation) {
  if (!reservation || reservation.schemaVersion !== 1 || !["PENDING", "COMPLETED"].includes(reservation.status) ||
      !Number.isSafeInteger(reservation.createdAtMs) || reservation.createdAtMs < 0) {
    throw new ArenaMutationReservationError("MUTATION_REUSE", "mutationId has an incompatible pending reservation");
  }
  const uid = id(reservation.uid, "reservation uid"); const mutationId = text(reservation.mutationId, "reservation mutationId");
  const operation = text(reservation.operation, "reservation operation");
  const documentId = id(reservation.documentId, "reservation documentId");
  const quarterId = id(reservation.quarterId, "reservation quarterId");
  const finalizationId = id(reservation.finalizationId, "reservation finalizationId");
  if (operation !== "FINALIZE_SEASON" || documentId !== mutationDocumentId(uid, mutationId)) {
    throw new ArenaMutationReservationError("MUTATION_REUSE", "reservation identity is incompatible");
  }
  const payload = {quarterId};
  const payloadHash = hash([operation, payload]);
  const requestDigest = hash([uid, mutationId, operation, payload]);
  if (reservation.payloadHash !== payloadHash || reservation.requestDigest !== requestDigest) {
    throw new ArenaMutationReservationError("MUTATION_REUSE", "reservation request binding is incompatible");
  }
  return {uid, mutationId, operation, documentId, quarterId, finalizationId, payload, payloadHash, requestDigest};
}

function verifyReservation(reservation, request) {
  const bound = validateReservationBinding(reservation);
  if (bound.documentId !== request.documentId || bound.uid !== request.uid || bound.mutationId !== request.mutationId ||
      bound.operation !== request.operation || bound.payloadHash !== request.payloadHash ||
      bound.requestDigest !== request.requestDigest) {
    throw new ArenaMutationReservationError("MUTATION_REUSE", "mutationId has an incompatible pending reservation");
  }
  return reservation;
}

/** Read both permanent records before any business writes in all Arena mutation transactions. */
async function resolveMutation(tx, request) {
  const receipt = data(await tx.get(request.ref));
  const reservation = data(await tx.get(request.reservationRef));
  if (receipt) {
    verifyReceipt(receipt, request);
    if (reservation && verifyReservation(reservation, request).status !== "COMPLETED") {
      throw new ArenaMutationReservationError("INCONSISTENT_MUTATION", "final receipt disagrees with pending reservation");
    }
    return {kind: "REPLAY", receipt, reservation};
  }
  if (!reservation) return {kind: "NEW", reservation: null};
  verifyReservation(reservation, request);
  if (reservation.status !== "PENDING") {
    throw new ArenaMutationReservationError("INCONSISTENT_MUTATION", "completed reservation lacks immutable receipt");
  }
  if (request.operation === "FINALIZE_SEASON") return {kind: "PENDING", reservation};
  throw new ArenaMutationReservationError("MUTATION_REUSE", "mutationId is reserved by a pending Arena finalization");
}

function reservationRecord(request, {quarterId, finalizationId, now}) {
  id(quarterId, "quarterId"); id(finalizationId, "finalizationId"); integer(now, "server now");
  if (request.operation !== "FINALIZE_SEASON") {
    throw new ArenaMutationReservationError("INVALID_ARGUMENT", "only season finalization may reserve a mutation");
  }
  const record = {
    schemaVersion: 1, status: "PENDING", documentId: request.documentId,
    uid: request.uid, mutationId: request.mutationId, operation: request.operation,
    payloadHash: request.payloadHash, requestDigest: request.requestDigest,
    quarterId, finalizationId, createdAtMs: now,
  };
  verifyReservation(record, request);
  return record;
}

function reserveMutation(tx, request, details) {
  const record = reservationRecord(request, details);
  tx.create(request.reservationRef, record);
  return record;
}

function completeReservation(tx, request, reservation, now) {
  verifyReservation(reservation, request); integer(now, "server now");
  if (reservation.status !== "PENDING") {
    throw new ArenaMutationReservationError("INCONSISTENT_MUTATION", "reservation is not pending at publication");
  }
  tx.set(request.reservationRef, {...reservation, status: "COMPLETED", completedAtMs: now}, {merge: false});
}

function requestFromReservation({db, reservation}) {
  if (!db || typeof db.doc !== "function") throw new TypeError("Firestore db required");
  if (!reservation || typeof reservation.documentId !== "string") {
    throw new ArenaMutationReservationError("INCONSISTENT_MUTATION", "reservation is missing request identity");
  }
  const bound = validateReservationBinding(reservation);
  const request = createMutationRequest({db, uid: bound.uid, mutationId: bound.mutationId,
    operation: bound.operation, payload: bound.payload});
  verifyReservation(reservation, request);
  return request;
}

module.exports = {
  ArenaMutationReservationError,
  hash,
  mutationDocumentId,
  createMutationRequest,
  resolveMutation,
  reserveMutation,
  completeReservation,
  requestFromReservation,
  verifyReservation,
  validateReservationBinding,
  reservationRecord,
  MAX_MUTATION_ID_BYTES,
  assertMutationIdForWrite,
};
