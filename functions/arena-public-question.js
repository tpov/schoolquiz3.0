"use strict";

const {CONTENT_TYPE, REDACTED_TYPE, restoreContent, redact, STATUS} = require("./question-redaction");

const MODERN = new Set(Object.values(CONTENT_TYPE));
const BASE = ["type", "id", "difficulty", "text", "imageUrl"];
const MAX_BYTES = 500_000;
function object(value) { return value !== null && typeof value === "object" && !Array.isArray(value); }
function own(value, key) { return Object.hasOwn(value, key); }
function only(value, keys) { return object(value) && Object.keys(value).every((key) => keys.includes(key)); }
function nonempty(value) { return typeof value === "string" && value.trim().length > 0; }
function rows(value) {
  return Array.isArray(value) && value.length > 0 && value.every((row) =>
    only(row, ["id", "text"]) && nonempty(row.id) && typeof row.text === "string") &&
    new Set(value.map((row) => row.id)).size === value.length;
}

/** JSON.parse discards duplicate keys, but their original bytes would still reach the client.
 * This bounded syntax walk runs only after JSON.parse accepted the complete JSON. It checks
 * decoded keys (including escaped duplicates), not string content that happens to look like JSON.
 */
function uniqueObjectKeys(json) {
  let index = 0;
  const whitespace = () => { while (/\s/.test(json[index] || "x")) index++; };
  function string() {
    const start = index++;
    while (index < json.length) {
      if (json[index++] === "\\") index++;
      else if (json[index - 1] === '"') return JSON.parse(json.slice(start, index));
    }
    throw new Error("unterminated JSON string");
  }
  function value(depth) {
    if (depth > 64) return false;
    whitespace();
    if (json[index] === '"') { string(); return true; }
    if (json[index] === "{" || json[index] === "[") {
      const isObject = json[index++] === "{";
      const end = isObject ? "}" : "]"; const keys = new Set();
      whitespace();
      if (json[index] === end) { index++; return true; }
      while (index < json.length) {
        whitespace();
        if (isObject) {
          const key = string();
          if (keys.has(key)) return false;
          keys.add(key); whitespace(); index++; // colon; syntax was already validated
        }
        if (!value(depth + 1)) return false;
        whitespace();
        if (json[index++] === end) return true;
      }
      return false;
    }
    while (index < json.length && !/[\s,\]}]/.test(json[index])) index++;
    return true;
  }
  try { const valid = value(0); whitespace(); return valid && index === json.length; } catch (_) { return false; }
}
function parseQuestionPayload(payload) {
  if (typeof payload !== "string" || Buffer.byteLength(payload) > MAX_BYTES) return null;
  try {
    const content = JSON.parse(payload);
    return object(content) && uniqueObjectKeys(payload) ? content : null;
  } catch (_) { return null; }
}

/** Stamp missing modern metadata from the selected, trusted document wrapper before freezing.
 * A present conflicting/malformed value is never repaired. Legacy dialects are not converted;
 * the existing redactor/scorer retains its own support/refusal policy for them.
 */
function normalizeModernQuestion(content, {questionId, difficulty}) {
  if (!object(content) || !nonempty(questionId) || !["EASY", "HARD"].includes(difficulty)) return null;
  if ((own(content, "id") && content.id !== questionId) ||
      (own(content, "difficulty") && content.difficulty !== difficulty)) return null;
  return MODERN.has(content.type) ? {...content, id: questionId, difficulty} : content;
}

/** Strict public wire boundary; never repairs an immutable issued payload or reissues row IDs.
 * The private key is checked only in memory using the established restoration engine. It is
 * necessary to reject answer-bearing protectedTextSegments that bypassed the original emitter.
 * Returns a boolean, not a reconstructed question or key, and does not mutate either argument.
 */
function safeRedactedOrSurvey(payload, {questionId, difficulty, gradingKey}) {
  const content = parseQuestionPayload(payload);
  if (!content || !nonempty(questionId) ||
      !["EASY", "HARD"].includes(difficulty) ||
      (own(content, "id") && content.id !== questionId) ||
      (own(content, "difficulty") && content.difficulty !== difficulty) ||
      typeof content.text !== "string" || !own(content, "imageUrl") ||
      !(content.imageUrl === null || typeof content.imageUrl === "string")) return false;
  if (content.type === CONTENT_TYPE.SURVEY) {
    return only(content, [...BASE, "options", "allowMultiple", "info"]) &&
      content.id === questionId && content.difficulty === difficulty && nonempty(content.text) &&
      rows(content.options) && content.options.length >= 2 && content.options.length <= 8 &&
      (!own(content, "allowMultiple") || typeof content.allowMultiple === "boolean") &&
      (!own(content, "info") || content.info === null || typeof content.info === "string") &&
      gradingKey?.kind === "PARTICIPATION";
  }
  let valid = false;
  switch (content.type) {
    case REDACTED_TYPE.SingleChoice:
    case REDACTED_TYPE.MultipleChoice:
      valid = only(content, [...BASE, "options"]) && rows(content.options); break;
    case REDACTED_TYPE.Ordering:
      valid = only(content, [...BASE, "items"]) && rows(content.items); break;
    case REDACTED_TYPE.FillBlank:
      valid = only(content, [...BASE, "blanks", "candidates", "protectedTextSegments"]) &&
        Array.isArray(content.blanks) && content.blanks.length > 0 && content.blanks.every(nonempty) &&
        new Set(content.blanks).size === content.blanks.length && rows(content.candidates) &&
        (!own(content, "protectedTextSegments") || (Array.isArray(content.protectedTextSegments) &&
          content.protectedTextSegments.every((segment) => typeof segment === "string"))); break;
    default: return false;
  }
  // The established emitter writes questionId:null when the old source payload had no id.
  // In that case the immutable ticket row / key-document entry supplies the binding.
  if (!valid || (gradingKey?.questionId != null && gradingKey.questionId !== "" && gradingKey.questionId !== questionId)) return false;
  try {
    const restored = restoreContent(content, gradingKey);
    if (!restored) return false;
    // Reuse the emitter's key/row invariants rather than duplicate grading rules. This temporary
    // validation result is discarded: original public IDs, ordering and bytes are never replaced.
    if (redact(JSON.stringify(restored), difficulty, {questionId, random: () => 0}).status !== STATUS.REDACTED) return false;
    if (content.type === REDACTED_TYPE.FillBlank && content.protectedTextSegments?.length) {
      const textById = new Map(restored.candidates.map((row) => [row.id, row.text]));
      const answers = restored.blanks.map((blank) => textById.get(blank.correctCandidateId).trim().toLowerCase()).filter(Boolean);
      if (content.protectedTextSegments.some((segment) => answers.some((answer) => segment.trim().toLowerCase().includes(answer)))) return false;
    }
  } catch (_) { return false; }
  return true;
}

module.exports = {parseQuestionPayload, normalizeModernQuestion, safeRedactedOrSurvey};
