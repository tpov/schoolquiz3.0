"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  COVERAGE_SCHEMA_VERSION,
  assessmentStateId,
  confirmedCoverage,
  coverageDocumentId,
  coverageVerdict,
  materializationPlan,
  requiredManifest,
  themePassRegistry,
} = require("./exam-coverage");

function row(id, revision, difficulty = "EASY", survey = false) {
  const content = survey ?
    {id, type: "Survey", text: "s", difficulty, options: [{id: "a", text: "o"}]} :
    {id, type: "SingleChoice", text: "q", difficulty, options: [{id: "a", text: "x"}],
      correctOptionId: "a"};
  return {questionId: id, revision, difficulty, content, survey, version: revision};
}

function settledAttempt({lessonId = "lesson", served, codeAnswer, completed = true} = {}) {
  return {
    lessonId,
    completed,
    settlementReceipt: {status: completed ? "SETTLED" : "REJECTED"},
    served,
    codeAnswer,
  };
}

test("the required manifest lists exact canonical EASY tokens and hashes them", () => {
  const manifest = requiredManifest("lesson", [
    row("q1", 3), row("q2", 1), row("q3", 2, "HARD"), row("s1", 1, "EASY", true),
  ]);
  assert.equal(manifest.lessonId, "lesson");
  assert.deepEqual(manifest.requiredTokens, [
    {questionId: "q1", revision: 3},
    {questionId: "q2", revision: 1},
  ], "HARD и Survey в требуемом наборе не участвуют");
  assert.equal(manifest.manifestHash.length, 64);
  assert.equal(requiredManifest("lesson", [row("q1", 3)]).manifestHash !== manifest.manifestHash, true,
    "другой набор — другой манифест");
});

test("an empty or unknown manifest is unavailable, never vacuously complete", () => {
  assert.equal(requiredManifest("lesson", []), null);
  assert.equal(requiredManifest("lesson", [row("q1", 1, "HARD")]), null, "только HARD — не EASY-манифест");
});

test("confirmed coverage unions full-credit EASY tokens across settled attempts", () => {
  const confirmed = confirmedCoverage([
    settledAttempt({served: [
      {questionId: "q1", revision: 3, difficulty: "EASY"},
      {questionId: "q2", revision: 1, difficulty: "EASY"},
    ], codeAnswer: "99"}),
    // Вторая попытка: повтор q1 один раз, плюс новые токены.
    settledAttempt({served: [
      {questionId: "q1", revision: 3, difficulty: "EASY"},
      {questionId: "q3", revision: 2, difficulty: "EASY"},
    ], codeAnswer: "99"}),
  ]);
  assert.equal(confirmed.confirmedTokens.size, 3);
  assert.ok(confirmed.confirmedTokens.has("q1\u00003"));
  assert.ok(confirmed.confirmedTokens.has("q3\u00002"));
  assert.equal(confirmed.unknownProvenance, false);
});

test("partial credit, HARD digits and aborted attempts prove nothing", () => {
  const confirmed = confirmedCoverage([
    settledAttempt({served: [{questionId: "q1", revision: 1, difficulty: "EASY"}], codeAnswer: "8"}),
    settledAttempt({served: [{questionId: "q2", revision: 1, difficulty: "HARD"}], codeAnswer: "9"}),
    settledAttempt({completed: false, served: [{questionId: "q3", revision: 1, difficulty: "EASY"}],
      codeAnswer: "9"}),
  ]);
  assert.equal(confirmed.confirmedTokens.size, 0);
});

test("legacy attempts without served evidence stay pending, not zero", () => {
  const legacy = {lessonId: "lesson", completed: true, settlementReceipt: {status: "SETTLED"},
    codeAnswer: "99999999999999999999"};
  const confirmed = confirmedCoverage([legacy]);
  assert.equal(confirmed.confirmedTokens.size, 0);
  assert.equal(confirmed.unknownProvenance, true);
});

test("coverage verdict distinguishes Locked, ResultsNotSynced and ok", () => {
  const manifest = requiredManifest("lesson", [row("q1", 3), row("q2", 1)]);
  const nothing = confirmedCoverage([]);
  assert.equal(coverageVerdict(manifest, null, nothing).state, "Locked");

  const partial = confirmedCoverage([settledAttempt({served: [
    {questionId: "q1", revision: 3, difficulty: "EASY"}], codeAnswer: "9"})]);
  assert.equal(coverageVerdict(manifest, null, partial).state, "ResultsNotSynced",
    "заработано, но требуемый набор не полон");

  const full = confirmedCoverage([settledAttempt({served: [
    {questionId: "q1", revision: 3, difficulty: "EASY"},
    {questionId: "q2", revision: 1, difficulty: "EASY"}], codeAnswer: "99"})]);
  assert.equal(coverageVerdict(manifest, null, full).state, "ResultsNotSynced",
    "полный набор без материализации — всё ещё барьер");

  const plan = materializationPlan({uid: "u", manifest,
    confirmedTokens: full.confirmedTokens, nowMs: 5});
  assert.equal(plan.complete, true);
  assert.equal(coverageVerdict(manifest, plan, full).state, "ok");
  // Смена манифеста обнуляет материализацию: новые токены требуют свежего доказательства.
  const revised = requiredManifest("lesson", [row("q1", 3), row("q2", 1), row("q4", 2)]);
  assert.equal(coverageVerdict(revised, plan, full).state, "ResultsNotSynced");
  assert.throws(() => materializationPlan({uid: "u", manifest: revised,
    confirmedTokens: full.confirmedTokens, nowMs: 5}),
    (error) => error.code === "COVERAGE_INCOMPLETE" && /q4/.test(error.message));
});

test("unknown manifest and legacy provenance are unavailable/pending, not refusal-by-zero", () => {
  assert.equal(coverageVerdict(null, null, confirmedCoverage([])).state, "Unavailable");
  const legacy = coverageVerdict(requiredManifest("lesson", [row("q1", 1)]), null,
    {confirmedTokens: new Map(), unknownProvenance: true});
  assert.equal(legacy.state, "ResultsNotSynced", "наследие — ожидание, а не замок");
});

test("schema version binds the materialization shape", () => {
  const manifest = requiredManifest("lesson", [row("q1", 1)]);
  const full = confirmedCoverage([settledAttempt({served: [
    {questionId: "q1", revision: 1, difficulty: "EASY"}], codeAnswer: "9"})]);
  const plan = materializationPlan({uid: "u", manifest, confirmedTokens: full.confirmedTokens,
    nowMs: 1});
  assert.equal(plan.schemaVersion, COVERAGE_SCHEMA_VERSION);
  assert.equal(coverageVerdict(manifest, {...plan, schemaVersion: 99}, full).state, "ResultsNotSynced");
  assert.equal(coverageDocumentId("u", "lesson").length, 64);
});

test("theme pass registry keys by stable theme id and ignores archived extras", () => {
  const states = new Map([
    ["t1", {passed: true}],
    ["t2", {passed: false}],
  ]);
  const registry = themePassRegistry(states, ["t1"]);
  assert.deepEqual(registry.missing, [], "пройденная тема в missing не стоит");
  assert.equal(registry.complete, true);
  assert.deepEqual(themePassRegistry(states, ["t1", "t2"]).missing, ["t2"]);
  assert.equal(themePassRegistry(states, ["t1", "t2", "t3"]).complete, false, "новая тема требует прохода");
  // Архивные темы в требуемом списке не стоят — решение читающего, не реестра.
  assert.equal(themePassRegistry(states, ["t1", "t9"]).missing.includes("t9"), true);
  assert.equal(assessmentStateId("u", "ThemeTest", "t1").length, 64);
});
