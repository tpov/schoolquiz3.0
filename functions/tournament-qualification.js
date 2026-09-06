"use strict";

const {calculateTournamentLeaderboard, ALGORITHM_VERSION} = require("./tournament-ranking");
const {tournamentDigest} = require("./tournament-bank");

/** Worker-side helper, NOT a client request decoder. Caller has closed/settled every entry at
 * cutoff and obtained these rows from authenticated server sessions, including timed-out runs.
 * Run outside the financial transaction: the existing LS implementation is not a bounded query.
 * Store the result immutably; the transaction loader must attest that its freeze is complete.
 */
function buildQualificationRankingSnapshot({eventId, forms, endsAtMs, snapshotId, verifiedRuns, completeThroughMs, pendingSessionCount}) {
  if (typeof eventId !== "string" || !eventId || typeof snapshotId !== "string" || !snapshotId ||
      !Number.isSafeInteger(endsAtMs) || endsAtMs < 0 || !Array.isArray(forms) || forms.length !== 3 || !Array.isArray(verifiedRuns)) {
    throw new TypeError("Immutable event/forms/cutoff/verified runs required");
  }
  if (completeThroughMs !== endsAtMs || pendingSessionCount !== 0) {
    throw new RangeError("Explicit complete cutoff and zero pending server sessions required");
  }
  const groups = new Map(forms.map((form) => [form.formId, {groupId: form.formId, results: []}]));
  if (groups.size !== 3) throw new RangeError("Three distinct qualification forms required");
  const seen = new Set(); const sessionIds = new Set(); const rows = [];
  for (const run of verifiedRuns) {
    const form = forms.find((item) => item.formId === run.formId);
    if (!form || run.eventId !== eventId || run.formFingerprint !== form.fingerprint || run.status !== "SETTLED" ||
        typeof run.uid !== "string" || !run.uid || typeof run.sessionId !== "string" || !run.sessionId ||
        typeof run.verificationVersion !== "string" || !run.verificationVersion ||
        !Number.isSafeInteger(run.percentScore) || run.percentScore < 0 || run.percentScore > 100 ||
        !Number.isSafeInteger(run.elapsedMs) || run.elapsedMs < 0 || run.elapsedMs > form.maxDurationMs ||
        !Number.isSafeInteger(run.startedAtMs) || run.startedAtMs < 0 || run.startedAtMs >= endsAtMs ||
        !Number.isSafeInteger(run.completedAtMs) || run.completedAtMs < run.startedAtMs || run.completedAtMs > endsAtMs ||
        run.completedAtMs - run.startedAtMs !== run.elapsedMs) {
      throw new RangeError("Qualification row is not a bound, settled server result");
    }
    const key = JSON.stringify([run.uid, run.formId]);
    if (seen.has(key) || sessionIds.has(run.sessionId)) throw new RangeError("Multiple entries for one player/form or repeated session identity");
    seen.add(key); sessionIds.add(run.sessionId);
    const row = {uid: run.uid, sessionId: run.sessionId, formId: run.formId, formFingerprint: run.formFingerprint,
      verificationVersion: run.verificationVersion, percentScore: run.percentScore,
      elapsedMs: run.elapsedMs, startedAtMs: run.startedAtMs, completedAtMs: run.completedAtMs};
    rows.push(row);
    groups.get(run.formId).results.push({userId: run.uid, percent: run.percentScore,
      elapsedMs: run.elapsedMs, completedAtMs: run.completedAtMs});
  }
  rows.sort((a, b) => {
    const left = JSON.stringify([a.formId, a.uid]); const right = JSON.stringify([b.formId, b.uid]);
    return left < right ? -1 : left > right ? 1 : 0;
  });
  const ranked = calculateTournamentLeaderboard([...groups.values()]);
  return {schemaVersion: 1, eventId, snapshotId, complete: true, endsAtMs, algorithmVersion: ALGORITHM_VERSION,
    formsFingerprint: tournamentDigest(forms.map(({formId, fingerprint}) => ({formId, fingerprint}))),
    verifiedRunsFingerprint: tournamentDigest(rows), verifiedRunCount: rows.length,
    totalRanked: ranked.leaderboard.length,
    // Pure policy needs only these authoritative ordinals; do not persist Infinity timing fields.
    ranking: ranked.leaderboard.slice(0, 16).map(({userId, place}) => ({uid: userId, place}))};
}

module.exports = {buildQualificationRankingSnapshot};
