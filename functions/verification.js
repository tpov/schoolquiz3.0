"use strict";

/**
 * Account verification: the details a person supplies about themselves so an admin or developer can
 * check who they are, and the rules those details must satisfy before a reviewer's time is spent.
 *
 * This is personal data. It is validated here rather than at the call site so the shape is defined
 * in exactly one place, and kept deliberately small: a name, a birthday, a city and a telegram
 * handle to reach them on. Nothing else is asked for, because nothing else is needed to hold a
 * conversation and decide.
 */

const MAX_TEXT_LENGTH = 100;
const MIN_AGE_YEARS = 5;
const MAX_AGE_YEARS = 120;

/** Telegram's own rule: 5–32 characters of letters, digits and underscores. */
const TELEGRAM_PATTERN = /^[A-Za-z0-9_]{5,32}$/;
const ISO_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

const DECISION_APPROVED = "APPROVED";
const DECISION_REJECTED = "REJECTED";

class VerificationError extends Error {}

function requireText(value, field) {
  const text = String(value === null || value === undefined ? "" : value).trim();
  if (!text) throw new VerificationError(`${field} is required`);
  if (text.length > MAX_TEXT_LENGTH) {
    throw new VerificationError(`${field} must be at most ${MAX_TEXT_LENGTH} characters`);
  }
  return text;
}

/** Accepts "@name" or "name" and stores the bare handle, so reviewers see one consistent form. */
function normalizeTelegram(value) {
  const raw = String(value === null || value === undefined ? "" : value).trim().replace(/^@+/, "");
  if (!TELEGRAM_PATTERN.test(raw)) {
    throw new VerificationError("telegram must be 5-32 characters of letters, digits or underscore");
  }
  return raw;
}

/**
 * A birthday has to be a real calendar date, not merely digits in the right places: 2026-02-31
 * matches the pattern and is not a day. Round-tripping through Date catches that.
 */
function normalizeBirthday(value, nowMs) {
  const raw = String(value === null || value === undefined ? "" : value).trim();
  const match = ISO_DATE_PATTERN.exec(raw);
  if (!match) throw new VerificationError("birthday must be in YYYY-MM-DD form");

  const [, year, month, day] = match.map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day));
  if (
    parsed.getUTCFullYear() !== year ||
    parsed.getUTCMonth() !== month - 1 ||
    parsed.getUTCDate() !== day
  ) {
    throw new VerificationError("birthday is not a real date");
  }

  const ageYears = (nowMs - parsed.getTime()) / (365.2425 * 24 * 60 * 60 * 1000);
  if (ageYears < MIN_AGE_YEARS || ageYears > MAX_AGE_YEARS) {
    throw new VerificationError("birthday is out of the plausible range");
  }
  return raw;
}

function normalizeVerificationDetails(data, nowMs) {
  const source = data || {};
  return {
    realName: requireText(source.realName, "realName"),
    birthday: normalizeBirthday(source.birthday, nowMs),
    city: requireText(source.city, "city"),
    telegram: normalizeTelegram(source.telegram),
  };
}

/**
 * Whether a fresh request may be filed.
 *
 * An approved account has nothing to ask for, and a request already waiting must not be replaced —
 * otherwise somebody could rewrite their details after a reviewer had started reading them. A
 * rejection, by contrast, is meant to be answered: fix what was wrong and apply again.
 */
function canSubmitRequest(existingRequest, isVerified) {
  if (isVerified) return {allowed: false, reason: "already-verified"};
  if (!existingRequest) return {allowed: true};
  const status = String(existingRequest.status || "").toUpperCase();
  if (status === DECISION_APPROVED) return {allowed: false, reason: "already-verified"};
  if (status === DECISION_REJECTED) return {allowed: true};
  return {allowed: false, reason: "already-pending"};
}

function normalizeDecision(value) {
  const decision = String(value || "").trim().toUpperCase();
  if (decision !== DECISION_APPROVED && decision !== DECISION_REJECTED) {
    throw new VerificationError(`decision must be ${DECISION_APPROVED} or ${DECISION_REJECTED}`);
  }
  return decision;
}

module.exports = {
  DECISION_APPROVED,
  DECISION_REJECTED,
  VerificationError,
  normalizeVerificationDetails,
  normalizeTelegram,
  normalizeBirthday,
  canSubmitRequest,
  normalizeDecision,
};
