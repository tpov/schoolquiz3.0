"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {tournamentDigest: digest} = require("./tournament-bank");
const {VERSIONS} = require("./tournament-qualification-compact");
const {INPUT_FINGERPRINT_VERSION, calculationContract, validateCalculationContract,
  assertCurrentCalculationRuntime, assertLegacyQualificationPublication, validateRankingVersion} = require("./tournament-qualification-version");

function event() {
  return {schemaVersion: 1, eventId: "event_version_00000001", status: "QUALIFICATION", lastMutationAtMs: 2000,
    qualification: {endsAtMs: 2000}, qualificationForms: [1, 2, 3].map(n => ({formId: `form-${n}`, fingerprint: digest(n)})),
    source: {kind: "COURSE", id: "course", snapshotId: "v1"}, poolFingerprint: digest("pool"), privateBankFingerprint: digest("private"), timingVersion: "time-v1"};
}
function selected() {
  const value = event(); const contract = calculationContract(value);
  value.qualificationCalculation = contract;
  value.qualificationFreezeFence = {schemaVersion: 1, freezeId: contract.freezeId, endsAtMs: contract.endsAtMs, formsFingerprint: contract.formsFingerprint, fencedAtMs: 2000};
  return value;
}
function ranking(value) {
  const c = value.qualificationCalculation;
  return {schemaVersion: 2, algorithmVersion: VERSIONS.algorithmFamily, snapshotId: c.freezeId, freezeFenceId: c.freezeId,
    calculationContractDigest: c.contractDigest, calculationVersions: {...VERSIONS}, inputFingerprintVersion: INPUT_FINGERPRINT_VERSION};
}
test("deterministic version contract binds all immutable inputs and exact core semantics", () => {
  const value = selected(); const original = structuredClone(value);
  const c = validateCalculationContract(value);
  assert.deepEqual(c.versions, VERSIONS); assert.deepEqual(calculationContract(value), c); assert.deepEqual(value, original);
  assert.deepEqual(validateCalculationContract(JSON.parse(JSON.stringify(value))), c);
  const {contractDigest, freezeId, ...anchor} = c;
  assert.equal(contractDigest, digest(anchor)); assert.equal(freezeId, `freeze_${digest(["qualification-calculation", contractDigest])}`);
  const legacyId = `freeze_${digest([value.eventId, c.endsAtMs, c.formsFingerprint])}`;
  assert.notEqual(c.freezeId, legacyId); assert.equal(validateCalculationContract(event()), null);
});
test("changed source, forms, timing, clock, version or fence cannot resume selected work", () => {
  for (const mutate of [e => { e.source.snapshotId = "v2"; }, e => { e.qualificationForms[0].fingerprint = digest("changed"); },
    e => { e.timingVersion = "time-v2"; }, e => { e.qualification.endsAtMs++; }, e => { e.lastMutationAtMs--; },
    e => { e.qualificationCalculation.versions.executionVersion = "unknown"; }, e => { e.qualificationFreezeFence.freezeId = "old"; },
    e => { e.qualificationCalculation.contractDigest = digest("forged"); }, e => { e.qualificationCalculation = null; }]) {
    const value = selected(); mutate(value); assert.throws(() => validateCalculationContract(value), {code: "UNAVAILABLE"});
  }
});
test("legacy publisher accepts only its original unversioned fence", () => {
  const legacy = event(); legacy.qualificationFreezeFence = {freezeId: "legacy-fence"};
  assert.doesNotThrow(() => assertLegacyQualificationPublication(legacy, "legacy-fence"));
  assert.throws(() => assertLegacyQualificationPublication(legacy, "other-fence"), {code: "UNAVAILABLE"});
  const value = selected(); assert.throws(() => assertLegacyQualificationPublication(value, value.qualificationFreezeFence.freezeId), {code: "UNAVAILABLE"});
});
test("ranking schema and version cannot cross legacy/selected event boundaries", () => {
  const value = selected(); const v2 = ranking(value); const old = {schemaVersion: 1, algorithmVersion: VERSIONS.algorithmFamily};
  assert.deepEqual(validateRankingVersion(old, event()), {});
  assert.throws(() => validateRankingVersion(old, value), {code: "UNAVAILABLE"});
  assert.throws(() => validateRankingVersion(v2, event()), {code: "UNAVAILABLE"});
  const provenance = validateRankingVersion(v2, value);
  assert.equal(provenance.calculationContractDigest, value.qualificationCalculation.contractDigest);
  assert.deepEqual(provenance.calculationVersions, VERSIONS); assert.equal(provenance.inputFingerprintVersion, INPUT_FINGERPRINT_VERSION);
  for (const mutate of [r => { r.calculationContractDigest = digest("other"); }, r => { r.calculationVersions.roundingVersion = "floating"; },
    r => { r.inputFingerprintVersion = "legacy-array"; }, r => { r.schemaVersion = 1; }, r => { r.freezeFenceId = "old"; }]) {
    const changed = structuredClone(v2); mutate(changed); assert.throws(() => validateRankingVersion(changed, value), {code: "UNAVAILABLE"});
  }
});
test("published version provenance remains readable after runtime upgrade, while new computation refuses it", () => {
  const value = selected(); const old = structuredClone(value.qualificationCalculation);
  old.collation.icu = "historical-test-runtime";
  const {contractDigest, freezeId, ...anchor} = old; old.contractDigest = digest(anchor);
  old.freezeId = `freeze_${digest(["qualification-calculation", old.contractDigest])}`;
  value.qualificationCalculation = old; value.qualificationFreezeFence.freezeId = old.freezeId;
  assert.deepEqual(validateCalculationContract(value), old);
  assert.doesNotThrow(() => validateRankingVersion(ranking(value), value));
  assert.throws(() => assertCurrentCalculationRuntime(old), {code: "UNAVAILABLE"});
  assert.doesNotThrow(() => assertCurrentCalculationRuntime(calculationContract(event())));
});
