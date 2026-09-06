"use strict";

const {computePercentScore} = require("./assessment-scoring");
const {TOURNAMENT_PATHS} = require("./tournament-service");
const {VERIFIED_RUNS, VERIFICATION_VERSION, QUESTION_COUNT} = require("./tournament-session");

function gap(message) { const error = new Error(message); error.code = "UNAVAILABLE"; throw error; }
function verifiedTournamentRun(session, run, expected, nowMs) {
  if (!session || session.sessionId !== expected.sessionId || session.uid !== expected.uid || session.eventId !== expected.eventId ||
      session.formFingerprint !== expected.formFingerprint || (expected.matchId && session.matchId !== expected.matchId)) {
    gap("Authoritative session binding is unavailable");
  }
  if (session.status !== "SETTLED") {
    if (run) gap("Verified result exists before its session settled");
    return null;
  }
  if (!run || session.pendingAnswerOperationId || session.questionIndex !== QUESTION_COUNT || run.schemaVersion !== 1 ||
      run.status !== "SETTLED" || run.verificationVersion !== VERIFICATION_VERSION || run.servedQuestionCount !== QUESTION_COUNT ||
      run.sessionId !== session.sessionId || run.uid !== session.uid || run.eventId !== session.eventId ||
      run.kind !== session.kind || run.formId !== session.formId || run.formFingerprint !== session.formFingerprint ||
      (session.kind === "MATCH" && run.matchId !== session.matchId) ||
      (session.kind === "QUALIFICATION" && run.roundNumber !== session.roundNumber) ||
      !/^[1-9]{20}$/.test(run.codeAnswer || "") || run.codeAnswer !== session.scoreDigits ||
      run.percentScore !== computePercentScore(run.codeAnswer) || run.startedAtMs !== session.startedAtMs ||
      !Number.isSafeInteger(run.completedAtMs) || run.completedAtMs !== session.completedAtMs ||
      run.completedAtMs < session.startedAtMs || run.completedAtMs > Math.min(nowMs, session.endsAtMs) ||
      run.elapsedMs !== run.completedAtMs - session.startedAtMs) {
    gap("Verified run is missing or disagrees with its immutable server session");
  }
  return structuredClone(run);
}

/** Direct injection into tournament-service; transaction reads only, no client-result fallbacks. */
async function loadSettledMatchResults({db, transaction, eventId, matchId, sessions, nowMs}) {
  if (!Array.isArray(sessions) || sessions.length !== 2 || sessions[0].uid === sessions[1].uid) gap("Two bound server session descriptors required");
  const values = await transaction.getAll(...sessions.flatMap(session => [
    db.doc(`${TOURNAMENT_PATHS.sessions}/${session.sessionId}`), db.doc(`${VERIFIED_RUNS}/${session.sessionId}`),
  ]));
  const results = sessions.map((session, i) => {
    if (session.eventId !== eventId || session.matchId !== matchId || session.kind !== "MATCH") gap("Wrong match session descriptor");
    return verifiedTournamentRun(values[i * 2].exists ? values[i * 2].data() : null,
      values[i * 2 + 1].exists ? values[i * 2 + 1].data() : null, {...session, eventId, matchId}, nowMs);
  });
  return results.every(Boolean) ? results : null;
}

module.exports = {verifiedTournamentRun, loadSettledMatchResults};
