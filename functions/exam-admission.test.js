"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {FINAL_COOLDOWN_MS, OUTCOME, THEME_COOLDOWN_MS, cooldownVerdict, evaluateAdmission,
  termsVerdict, worstCoverage} = require("./exam-admission");

const OK_TERMS = {requiredTermsRevision: 1, acceptedTermsRevision: 1, shownTerms: true};

function okParams(over = {}) {
  return {
    openSitting: null,
    examEnabled: true,
    bankKnown: true,
    terms: OK_TERMS,
    pool: {drawable: true},
    certification: null,
    coverage: {complete: true, state: "ok"},
    cooldown: {closedAtMs: null},
    cooldownMs: THEME_COOLDOWN_MS,
    lifePoints: 100,
    price: 100,
    nowMs: 1_000_000,
    ...over,
  };
}

test("gate order: open sitting recovers before every other gate", () => {
  const verdict = evaluateAdmission(okParams({
    openSitting: {state: "OPEN", sittingId: "s1"},
    examEnabled: false,
    lifePoints: 0,
    cooldown: {closedAtMs: 1},
  }));
  assert.equal(verdict.kind, OUTCOME.RECOVER);
  assert.equal(verdict.sittingId, "s1");
  assert.equal(verdict.price, 0, "восстановление ничего не стоит");
});

test("explicit unavailable states: disabled exam, unknown bank", () => {
  assert.equal(evaluateAdmission(okParams({examEnabled: false})).kind, OUTCOME.UNAVAILABLE);
  assert.equal(evaluateAdmission(okParams({bankKnown: false})).kind, OUTCOME.UNAVAILABLE);
  const unavailable = evaluateAdmission(okParams({bankKnown: false}));
  assert.equal(unavailable.reason, "BANK_UNAVAILABLE");
});

test("terms: not shown or changed paid terms require renewed agreement", () => {
  assert.equal(termsVerdict({requiredTermsRevision: 1, acceptedTermsRevision: 1, shownTerms: true}).accepted, true);
  assert.equal(termsVerdict({requiredTermsRevision: 1, acceptedTermsRevision: 1, shownTerms: false}).accepted, false);
  assert.equal(termsVerdict({requiredTermsRevision: 2, acceptedTermsRevision: 1, shownTerms: true}).reason,
    "TERMS_CHANGED");
  const verdict = evaluateAdmission(okParams({terms: {...OK_TERMS, acceptedTermsRevision: 0}}));
  assert.equal(verdict.kind, OUTCOME.LOCKED);
  assert.equal(verdict.reason, "TERMS_CHANGED");
});

test("pool gate and final certification refuse before any money moves", () => {
  assert.equal(evaluateAdmission(okParams({pool: {drawable: false}})).kind, OUTCOME.POOL_TOO_SMALL);
  const ineligible = evaluateAdmission(okParams({
    certification: {eligible: false, qualifiedThemes: 11, requiredThemes: 12}}));
  assert.equal(ineligible.kind, OUTCOME.UNAVAILABLE, "курс без 12 тем — явная недоступность");
  assert.equal(ineligible.reason, "COURSE_NOT_CERTIFICATION_ELIGIBLE");
});

test("theme coverage: incomplete is Locked, un-ingested evidence is ResultsNotSynced", () => {
  assert.equal(evaluateAdmission(okParams({coverage: {complete: false, state: "Locked"}})).kind,
    OUTCOME.LOCKED);
  const pending = evaluateAdmission(okParams({coverage: {complete: false, state: "ResultsNotSynced"}}));
  assert.equal(pending.kind, OUTCOME.RESULTS_NOT_SYNCED);
  assert.equal(evaluateAdmission(okParams({coverage: {complete: false, state: "Unavailable"}})).kind,
    OUTCOME.UNAVAILABLE);
});

test("final progress: unpassed required theme is Locked; pending correction generation blocks", () => {
  const locked = evaluateAdmission(okParams({
    coverage: {complete: false, pendingGeneration: false, missing: ["t9"]}}));
  assert.equal(locked.kind, OUTCOME.LOCKED);
  const generation = evaluateAdmission(okParams({
    coverage: {complete: true, pendingGeneration: true}}));
  assert.equal(generation.kind, OUTCOME.UNAVAILABLE);
  assert.equal(generation.reason, "CORRECTION_PENDING");
});

test("cooldown is rolling from terminal closure with an inclusive boundary", () => {
  const closure = 1_000_000;
  const before = evaluateAdmission(okParams({cooldown: {closedAtMs: closure},
    nowMs: closure + THEME_COOLDOWN_MS - 1}));
  assert.equal(before.kind, OUTCOME.COOLDOWN);
  assert.equal(before.availableAtMs, closure + THEME_COOLDOWN_MS);
  const atBoundary = evaluateAdmission(okParams({cooldown: {closedAtMs: closure},
    nowMs: closure + THEME_COOLDOWN_MS}));
  assert.equal(atBoundary.kind, OUTCOME.OK, "грань T+24h включена");
  // Пересечение полуночи и недели ничего не сбрасывает: тот же момент реального времени.
  assert.equal(cooldownVerdict({closedAtMs: closure, cooldownMs: THEME_COOLDOWN_MS,
    nowMs: closure + THEME_COOLDOWN_MS - 60 * 1000}).available, false);
  assert.equal(FINAL_COOLDOWN_MS, 168 * 60 * 60 * 1000);
  assert.equal(THEME_COOLDOWN_MS, 24 * 60 * 60 * 1000);
});

test("insufficient charge refuses on STANDARD points only; plasma never pays admission", () => {
  const verdict = evaluateAdmission(okParams({lifePoints: 99, price: 100}));
  assert.equal(verdict.kind, OUTCOME.INSUFFICIENT_CHARGE);
  assert.equal(verdict.reason, "STANDARD_POINTS");
  // Оценщик вообще не видит плазменного входа: сигнатура не содержит plasma.
  const ok = evaluateAdmission(okParams({lifePoints: 200, price: 200}));
  assert.equal(ok.kind, OUTCOME.OK);
});

test("ok verdict carries the price that start will debit", () => {
  const verdict = evaluateAdmission(okParams({lifePoints: 500, price: 100}));
  assert.equal(verdict.kind, OUTCOME.OK);
  assert.equal(verdict.price, 100);
});

test("worst coverage reduces lesson verdicts to the admission input", () => {
  assert.deepEqual(worstCoverage([]), {complete: false, state: "Unavailable"});
  assert.deepEqual(worstCoverage([{state: "ok"}, {state: "ok"}]), {complete: true, state: "ok"});
  assert.equal(worstCoverage([{state: "ok"}, {state: "ResultsNotSynced"}]).state, "ResultsNotSynced");
  assert.equal(worstCoverage([{state: "Locked"}, {state: "Unavailable"}]).state, "Unavailable");
  assert.equal(worstCoverage([{state: "Locked"}, {state: "ok"}]).state, "Locked");
});
