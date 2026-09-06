"use strict";

const crypto = require("node:crypto");
const {recomputePercentScore, isWellFormedCodeAnswer} = require("./result-verification");

const STATUS = Object.freeze({
  SETTLED: "SETTLED", REJECTED: "REJECTED", UNSCORABLE: "UNSCORABLE", LEGACY: "LEGACY_RECORDED",
});

function canonical(value) {
  if (value === null || typeof value === "string" || typeof value === "boolean") return JSON.stringify(value);
  if (typeof value === "number" && Number.isFinite(value)) return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.keys(value).filter((key) => value[key] !== undefined).sort()
      .map((key) => `${JSON.stringify(key)}:${canonical(value[key])}`).join(",")}}`;
  }
  throw new TypeError("Attempt fingerprint requires a JSON payload");
}

/** Hash the submitted body BEFORE enriching it with time, prices or today's lesson contents. */
function attemptFingerprint(uid, body) {
  return crypto.createHash("sha256").update(canonical({uid, body})).digest("hex");
}

function amount(value, name) {
  if (!Number.isSafeInteger(value) || value < 0) throw new TypeError(`${name} must be a nonnegative safe integer`);
  return value;
}

/** Called only inside the same transaction that finalizes the result and its monetary effects. */
function attemptReceipt({event, status, settledAtMs, lifePointsSpent = 0, plasmaPointsSpent = 0,
  reward = {skillPoints: 0, nolics: 0}}) {
  if (!Object.values(STATUS).includes(status)) throw new TypeError("Unknown receipt status");
  for (const field of ["userId", "attemptId", "lessonId"]) {
    if (typeof event[field] !== "string" || !event[field]) throw new TypeError(`Missing ${field}`);
  }
  const settled = status === STATUS.SETTLED;
  if (settled && (typeof event.codeAnswer !== "string" || !isWellFormedCodeAnswer(event.codeAnswer) ||
      event.percentScore !== recomputePercentScore(event.codeAnswer))) {
    throw new TypeError("A settled receipt requires the final verified score");
  }
  const life = amount(lifePointsSpent, "lifePointsSpent");
  const plasma = amount(plasmaPointsSpent, "plasmaPointsSpent");
  const skillPoints = amount(reward.skillPoints, "skillPoints");
  const nolics = amount(reward.nolics, "nolics");
  if (!settled && (life || plasma || skillPoints || nolics)) {
    throw new TypeError("Unsettled/legacy results cannot claim a new debit or reward");
  }
  return {
    schemaVersion: 1,
    receiptId: `attempt:${crypto.createHash("sha256").update(canonical([event.userId, event.attemptId])).digest("hex")}`,
    attemptId: event.attemptId,
    uid: event.userId,
    lessonId: event.lessonId,
    lessonVersion: amount(event.lessonVersion, "lessonVersion"),
    status,
    // Old records do not carry completion vs abort. Do not invent that proof on migration.
    completed: settled && event.completed === true,
    settledAtMs: amount(settledAtMs, "settledAtMs"),
    codeAnswer: settled ? event.codeAnswer : null,
    percentScore: settled ? event.percentScore : null,
    lifePointsSpent: life,
    plasmaPointsSpent: plasma,
    reward: {skillPoints, nolics},
  };
}

/** Never let another account's id or a changed retry overwrite the first settlement. */
function replayFault(stored, event, fingerprint) {
  if (stored.userId !== event.userId) return "OWNER_MISMATCH";
  if (stored.attemptId !== event.attemptId || stored.lessonId !== event.lessonId ||
      stored.lessonVersion !== event.lessonVersion || stored.scope !== event.scope) return "ATTEMPT_MISMATCH";
  if (stored.requestFingerprint && stored.requestFingerprint !== fingerprint) return "PAYLOAD_MISMATCH";
  return null;
}

module.exports = {STATUS, attemptFingerprint, attemptReceipt, replayFault};
