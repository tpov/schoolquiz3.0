"use strict";

const {createHash} = require("node:crypto");
const {restoreContent, redact} = require("./question-redaction");
const {preflightPool} = require("./tournament-policy");

function fail(message) { const error = new Error(message); error.code = "INVALID_BANK"; throw error; }
function canonical(value) {
  if (value === null || typeof value === "string" || typeof value === "boolean") return JSON.stringify(value);
  if (typeof value === "number" && Number.isSafeInteger(value)) return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonical).join(",")}]`;
  if (value && typeof value === "object" && Object.getPrototypeOf(value) === Object.prototype) {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonical(value[key])}`).join(",")}}`;
  }
  fail("Snapshot must contain only exact JSON values");
}
function digest(value) { return createHash("sha256").update(canonical(value)).digest("hex"); }
function fields(object, allowed, label) {
  if (!object || typeof object !== "object" || Array.isArray(object) || Object.keys(object).some((key) => !allowed.includes(key))) {
    fail(`${label} contains unsupported or private fields`);
  }
}
function text(value, label) { if (typeof value !== "string" || !value.trim()) fail(`${label} is required`); return value; }
function publicPayload(raw, key, question) {
  let payload;
  try { payload = typeof raw === "string" ? JSON.parse(raw) : structuredClone(raw); } catch { fail("Invalid public JSON"); }
  const extra = {SingleChoice: ["options"], MultipleChoice: ["options"], Ordering: ["items"],
    FillBlank: ["blanks", "candidates", "protectedTextSegments"]}[question.type];
  if (!extra) fail("Unsupported assessment type");
  fields(payload, ["type", "id", "difficulty", "text", "imageUrl", ...extra], "Public payload");
  if (payload.type !== `${question.type}Redacted` || (payload.id != null && payload.id !== question.questionId) ||
      (payload.difficulty != null && payload.difficulty !== question.difficulty)) fail("Public question identity mismatch");
  if (typeof payload.text !== "string") fail("Question text must be a string");
  if (payload.imageUrl !== undefined && payload.imageUrl !== null && typeof payload.imageUrl !== "string") fail("Invalid public image URL");
  for (const name of ["options", "items", "candidates"]) {
    if (!extra.includes(name)) continue;
    if (!Array.isArray(payload[name]) || !payload[name].length) fail(`Missing ${name}`);
    const ids = new Set();
    for (const item of payload[name]) {
      fields(item, ["id", "text"], name);
      text(item.id, "Item id");
      if (typeof item.text !== "string") fail("Item text must be a string");
      if (ids.has(item.id)) fail("Duplicate public item id");
      ids.add(item.id);
    }
  }
  for (const name of ["blanks", "protectedTextSegments"]) {
    if (payload[name] !== undefined && (!Array.isArray(payload[name]) || payload[name].some((item) => typeof item !== "string"))) {
      fail(`Invalid ${name}`);
    }
  }
  const restored = restoreContent(payload, key);
  if (!key || key.version !== 1 || key.type !== question.type || key.questionId !== question.questionId || !restored ||
      redact(JSON.stringify(restored), question.difficulty, {questionId: question.questionId, random: () => 0}).status !== "redacted") {
    fail("Missing or mismatched private answer key");
  }
  // Public fingerprint never hashes answer keys: their low entropy makes public hashes unsafe.
  canonical(payload); canonical(key);
  return payload;
}

/** Only a trusted server loader may supply this object. The attestation means both halves
 * came from ONE redaction call and the timer came from a versioned server formula/config.
 * It is NOT a verification of a flag supplied by a player. Authorization belongs to the loader.
 */
function normalizeTournamentBank({bank, requesterUid, sourceRef}) {
  if (!bank || bank.authorizedUid !== requesterUid || bank.immutable !== true || !Array.isArray(bank.questions)) {
    fail("Trusted authorized immutable bank required");
  }
  if (!bank.source || ["kind", "id", "snapshotId"].some((key) => bank.source[key] !== sourceRef[key])) fail("Source snapshot mismatch");
  text(bank.timingVersion, "Server timing version");
  const questions = [];
  const byId = new Map();
  for (const raw of bank.questions) {
    if (!raw || typeof raw !== "object") fail("Invalid bank question");
    if (byId.has(raw.questionId)) fail("Duplicate bank question id");
    byId.set(raw.questionId, null);
    if (raw.type === "Survey" || raw.type === "SURVEY" || raw.reviewed !== true || raw.published !== true) {
      questions.push({questionId: raw.questionId, type: raw.type, reviewed: false, published: false});
      continue;
    }
    if (raw.publicHalfRedacted !== true || raw.keyContentVersion !== raw.contentVersion) fail("Unattested or stale public/private pair");
    const payload = publicPayload(raw.publicPayload, raw.answerKey, raw);
    const question = {questionId: raw.questionId, type: raw.type, difficulty: raw.difficulty, sourceId: raw.sourceId,
      reviewed: true, published: true, contentVersion: raw.contentVersion,
      contentHash: digest(payload), maxDurationMs: raw.maxDurationMs};
    questions.push(question);
    byId.set(question.questionId, {question, publicPayload: payload, answerKey: structuredClone(raw.answerKey)});
  }
  const pool = preflightPool({source: bank.source, questions});
  const ordered = [...pool.easyQuestions, ...pool.hardQuestions].sort((a, b) => a.questionId < b.questionId ? -1 : a.questionId > b.questionId ? 1 : 0);
  const privateFingerprint = digest({timingVersion: bank.timingVersion, source: pool.source,
    questions: ordered.map((q) => ({...q, answerKey: byId.get(q.questionId).answerKey}))});
  return {...pool, questions, byId, privateFingerprint, timingVersion: bank.timingVersion};
}

function tournamentFormBundle(form, bank) {
  const publicQuestions = [];
  const keys = [];
  for (const metadata of form.questions) {
    const pair = bank.byId.get(metadata.questionId);
    if (!pair) fail("Missing immutable question pair");
    if (pair.question.contentHash !== metadata.contentHash || pair.question.contentVersion !== metadata.contentVersion) fail("Form snapshot mismatch");
    publicQuestions.push({...metadata, publicPayload: structuredClone(pair.publicPayload)});
    keys.push({questionId: metadata.questionId, contentVersion: metadata.contentVersion, answerKey: structuredClone(pair.answerKey)});
  }
  return {
    publicForm: {schemaVersion: 1, formId: form.formId, fingerprint: form.fingerprint,
      difficulty: form.difficulty, maxDurationMs: form.maxDurationMs, questions: publicQuestions},
    privateForm: {schemaVersion: 1, formId: form.formId, fingerprint: form.fingerprint, timingVersion: bank.timingVersion, keys},
  };
}

module.exports = {canonicalTournamentJson: canonical, tournamentDigest: digest, normalizeTournamentBank, tournamentFormBundle};
