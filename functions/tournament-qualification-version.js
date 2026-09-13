"use strict";

// Shared server-side provenance/fencing rules. No client dispatch, database writes or ranking.
const {VERSIONS} = require("./tournament-qualification-compact");
const {tournamentDigest: digest} = require("./tournament-bank");

const INPUT_FINGERPRINT_VERSION = "player-stream-chain-v1";
const currentCollation = () => ({icu: process.versions.icu, locale: new Intl.Collator().resolvedOptions().locale});
function fail(message) { const error = new Error(message); error.code = "UNAVAILABLE"; throw error; }
function body(event, collation) {
  if (!event || event.schemaVersion !== 1 || !/^[A-Za-z0-9_-]{16,128}$/.test(event.eventId || "") ||
      !Number.isSafeInteger(event.qualification?.endsAtMs) || event.qualification.endsAtMs < 0 ||
      !Array.isArray(event.qualificationForms) || event.qualificationForms.length !== 3 ||
      new Set(event.qualificationForms.map(form => form.formId)).size !== 3) fail("Immutable qualification identity required");
  return {schemaVersion: 1, eventId: event.eventId, endsAtMs: event.qualification.endsAtMs,
    formsFingerprint: digest(event.qualificationForms.map(({formId, fingerprint}) => ({formId, fingerprint}))),
    sourceFingerprint: digest({source: event.source, poolFingerprint: event.poolFingerprint,
      privateBankFingerprint: event.privateBankFingerprint ?? null, timingVersion: event.timingVersion ?? null}),
    versions: {...VERSIONS}, collation};
}
function contractFor(event, collation) {
  const anchor = body(event, collation); const contractDigest = digest(anchor);
  return {...anchor, contractDigest, freezeId: `freeze_${digest(["qualification-calculation", contractDigest])}`};
}
function calculationContract(event) { return contractFor(event, currentCollation()); }
function validateCalculationContract(event) {
  if (event?.qualificationCalculation === undefined) return null;
  const contract = event.qualificationCalculation; const collation = contract?.collation;
  if (!collation || Object.getPrototypeOf(collation) !== Object.prototype || Object.keys(collation).length !== 2 ||
      typeof collation.icu !== "string" || !collation.icu || typeof collation.locale !== "string" || !collation.locale) fail("Calculation collation binding requires repair");
  const expected = contractFor(event, collation);
  if (digest(contract) !== digest(expected) || event.qualificationFreezeFence?.schemaVersion !== 1 ||
      event.qualificationFreezeFence.freezeId !== expected.freezeId ||
      event.qualificationFreezeFence.endsAtMs !== expected.endsAtMs ||
      event.qualificationFreezeFence.formsFingerprint !== expected.formsFingerprint ||
      !Number.isSafeInteger(event.lastMutationAtMs) || event.lastMutationAtMs < expected.endsAtMs) fail("Calculation contract or freeze identity changed");
  return contract;
}
function assertCurrentCalculationRuntime(contract) {
  if (!contract || digest(contract.collation) !== digest(currentCollation())) fail("Bound calculation requires its original UID collation runtime");
}
function assertLegacyQualificationPublication(event, expectedFreezeId) {
  if (event?.qualificationCalculation !== undefined || !expectedFreezeId || event?.qualificationFreezeFence?.freezeId !== expectedFreezeId) {
    fail("Legacy publisher is fenced from the selected calculation");
  }
}
/** Validate versions without recomputing a published ranking. A saved collation is readable
 * after runtime upgrades; unfinished work separately requires its original runtime above.
 */
function validateRankingVersion(snapshot, event) {
  const contract = validateCalculationContract(event);
  if (!contract) {
    if (snapshot?.schemaVersion !== 1 || snapshot.algorithmVersion !== VERSIONS.algorithmFamily ||
        ["calculationContractDigest", "calculationVersions", "inputFingerprintVersion"].some(key => Object.hasOwn(snapshot, key))) {
      fail("Legacy ranking version disagrees with its event");
    }
    return {};
  }
  if (snapshot?.schemaVersion !== 2 || snapshot.algorithmVersion !== VERSIONS.algorithmFamily ||
      snapshot.calculationContractDigest !== contract.contractDigest ||
      digest(snapshot.calculationVersions ?? null) !== digest(contract.versions) ||
      snapshot.inputFingerprintVersion !== INPUT_FINGERPRINT_VERSION || snapshot.snapshotId !== contract.freezeId ||
      snapshot.freezeFenceId !== contract.freezeId) fail("Ranking does not belong to the selected calculation version");
  return {calculationContractDigest: contract.contractDigest, calculationVersions: {...contract.versions},
    inputFingerprintVersion: INPUT_FINGERPRINT_VERSION};
}

module.exports = {INPUT_FINGERPRINT_VERSION, calculationContract, validateCalculationContract,
  assertCurrentCalculationRuntime, assertLegacyQualificationPublication, validateRankingVersion};
