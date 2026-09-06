"use strict";

const {TOURNAMENT_PATHS, qualificationSessionId} = require("./tournament-service");
const {VERIFIED_RUNS, advanceTournamentSession} = require("./tournament-session");
const {verifiedTournamentRun} = require("./tournament-session-results");
const {buildQualificationRankingSnapshot} = require("./tournament-qualification");
const {ALGORITHM_VERSION} = require("./tournament-ranking");
const {tournamentDigest: digest} = require("./tournament-bank");

const QUALIFICATION_RANKINGS = "course_tournament_rankings";
function fail(code, message) { const error = new Error(message); error.code = code; throw error; }
function exact(value, label, min = 0) {
  if (!Number.isSafeInteger(value) || value < min) fail("INVALID_ARGUMENT", `${label} must be an exact integer >= ${min}`);
  return value;
}
function formsFingerprint(forms) { return digest(forms.map(({formId, fingerprint}) => ({formId, fingerprint}))); }
function frozenGate(event, ranking) {
  if (!ranking || ranking.schemaVersion !== 1 || ranking.complete !== true || ranking.eventId !== event.eventId ||
      ranking.endsAtMs !== event.qualification?.endsAtMs || ranking.algorithmVersion !== ALGORITHM_VERSION ||
      ranking.formsFingerprint !== formsFingerprint(event.qualificationForms) || !event.qualificationFreezeFence ||
      ranking.freezeFenceId !== event.qualificationFreezeFence.freezeId || ranking.snapshotId !== ranking.freezeFenceId ||
      !/^[a-f0-9]{64}$/.test(ranking.verifiedRunsFingerprint || "") || !Number.isSafeInteger(ranking.totalRanked) ||
      ranking.totalRanked < 0 || !Array.isArray(ranking.ranking) || ranking.ranking.length !== Math.min(16, ranking.totalRanked)) {
    fail("UNAVAILABLE", "Existing ranking is not a complete fenced verified snapshot");
  }
  return ranking;
}

/** SERVER WORKER ONLY. Every lifecycle admission transaction reads/writes this same event and
 * clamps its clock to lastMutationAtMs. Advancing it to cutoff fences in-flight admission retries
 * BEFORE scanning, without changing the lifecycle status or inventing an eligibility window.
 */
async function fenceQualification({db, eventId, now}) {
  exact(now, "server time");
  if (typeof eventId !== "string" || !/^[A-Za-z0-9_-]{16,128}$/.test(eventId)) fail("INVALID_ARGUMENT", "Immutable event id required");
  return db.runTransaction(async transaction => {
    const ref = db.doc(`${TOURNAMENT_PATHS.events}/${eventId}`);
    const [saved, ranking] = await transaction.getAll(ref, db.doc(`${QUALIFICATION_RANKINGS}/${eventId}`));
    if (!saved.exists) fail("NOT_FOUND", "Tournament event not found");
    const event = saved.data();
    if (ranking.exists) return {event, existingRanking: frozenGate(event, ranking.data())};
    if (event.schemaVersion !== 1 || event.eventId !== eventId || event.status !== "QUALIFICATION" ||
        !Array.isArray(event.qualificationForms) || event.qualificationForms.length !== 3) fail("FAILED_PRECONDITION", "Qualification cannot be frozen from this state");
    const endsAtMs = exact(event.qualification?.endsAtMs, "qualification cutoff");
    if (now < endsAtMs) fail("FAILED_PRECONDITION", "Qualification is still open");
    const fingerprint = formsFingerprint(event.qualificationForms);
    const freezeId = `freeze_${digest([eventId, endsAtMs, fingerprint])}`;
    const existing = event.qualificationFreezeFence;
    if (existing && (existing.schemaVersion !== 1 || existing.freezeId !== freezeId || existing.endsAtMs !== endsAtMs || existing.formsFingerprint !== fingerprint)) {
      fail("UNAVAILABLE", "Qualification fence disagrees with immutable forms");
    }
    const at = Math.max(now, exact(event.lastMutationAtMs, "event clock"));
    const fence = existing || {schemaVersion: 1, freezeId, endsAtMs, formsFingerprint: fingerprint, fencedAtMs: at};
    const next = {...event, lastMutationAtMs: at, qualificationFreezeFence: fence};
    transaction.set(ref, next);
    return {event: next, fence};
  });
}

async function readQualificationEntryPage({db, eventId, afterEntryId, pageSize}) {
  exact(pageSize, "pageSize", 1);
  if (pageSize > 100) fail("INVALID_ARGUMENT", "Page size must be <=100");
  // Document-id order includes malformed entries too: ordering by a possibly missing sessionId
  // would silently omit an admission, incorrectly making a damaged scan appear complete.
  let query = db.collection(`${TOURNAMENT_PATHS.events}/${eventId}/entries`).orderBy("__name__");
  if (afterEntryId !== null) query = query.startAfter(afterEntryId);
  const snapshot = await query.limit(pageSize + 1).get();
  if (!snapshot || !Array.isArray(snapshot.docs) || snapshot.docs.length > pageSize + 1) fail("UNAVAILABLE", "Session page is malformed");
  const selected = snapshot.docs.slice(0, pageSize).map(doc => {
    const entry = doc.data();
    if (!entry || entry.eventId !== eventId || typeof entry.uid !== "string" || !entry.uid ||
        !Number.isInteger(entry.roundNumber) || entry.roundNumber < 1 || entry.roundNumber > 3 ||
        !/^[A-Za-z0-9_-]{16,128}$/.test(entry.attemptId || "") || doc.id !== digest([entry.uid, entry.roundNumber]) ||
        entry.sessionId !== qualificationSessionId(entry.attemptId)) fail("UNAVAILABLE", "Qualification query returned a malformed admission");
    return {entryId: doc.id, entry};
  });
  for (let i = 0; i < selected.length; i++) {
    const previous = i ? selected[i - 1].entryId : afterEntryId;
    if (previous !== null && selected[i].entryId <= previous) fail("UNAVAILABLE", "Admission page is not strictly advancing");
  }
  return {entries: selected, hasMore: snapshot.docs.length > pageSize,
    lastEntryId: selected.length ? selected.at(-1).entryId : afterEntryId};
}

/** Bounded worker invocation, NOT a client callable. Budget is explicit operational config,
 * not a product entrant cap. If exhausted, it returns incomplete and NEVER publishes ranking;
 * retry with a sufficient budget or a separately implemented durable multi-invocation scanner.
 */
async function freezeQualification({db, eventId, now, pageSize, maxPages}) {
  exact(pageSize, "pageSize", 1); exact(maxPages, "maxPages", 1);
  if (pageSize > 100) fail("INVALID_ARGUMENT", "Page size must be <=100");
  const {event, fence, existingRanking} = await fenceQualification({db, eventId, now});
  if (existingRanking) return {complete: true, replayed: true, ranking: existingRanking};
  const runs = []; const pending = []; const seenSessions = new Set();
  let after = null; let terminal = false; let pageCount = 0;
  for (; pageCount < maxPages; pageCount++) {
    const page = await readQualificationEntryPage({db, eventId, afterEntryId: after, pageSize});
    for (const {entry} of page.entries) {
      if (seenSessions.has(entry.sessionId)) fail("UNAVAILABLE", "Duplicate admitted session across scan pages");
      seenSessions.add(entry.sessionId);
      const savedSession = await db.doc(`${TOURNAMENT_PATHS.sessions}/${entry.sessionId}`).get();
      if (!savedSession.exists) { pending.push({sessionId: entry.sessionId, reason: "ADMITTED_SESSION_MISSING"}); continue; }
      const session = savedSession.data();
      const form = event.qualificationForms[session.roundNumber - 1];
      if (session.sessionId !== entry.sessionId || session.uid !== entry.uid || session.eventId !== eventId || session.kind !== "QUALIFICATION" ||
          session.roundNumber !== entry.roundNumber || session.formId !== entry.formId || session.formFingerprint !== entry.formFingerprint ||
          !form || session.formId !== form.formId || session.formFingerprint !== form.fingerprint || session.endsAtMs > fence.endsAtMs ||
          session.startedAtMs < event.qualification.startsAtMs || session.startedAtMs >= fence.endsAtMs) {
        pending.push({sessionId: session.sessionId, reason: "SESSION_BINDING"}); continue;
      }
      try {
        await advanceTournamentSession({db, sessionId: session.sessionId,
          operationId: `freeze_advance_${digest([fence.freezeId, session.sessionId])}`, now});
        const verified = await db.runTransaction(async transaction => {
          const [saved, run] = await transaction.getAll(db.doc(`${TOURNAMENT_PATHS.sessions}/${session.sessionId}`), db.doc(`${VERIFIED_RUNS}/${session.sessionId}`));
          return verifiedTournamentRun(saved.exists ? saved.data() : null, run.exists ? run.data() : null,
            {sessionId: session.sessionId, uid: session.uid, eventId, formFingerprint: form.fingerprint}, now);
        });
        if (!verified) pending.push({sessionId: session.sessionId, reason: "NOT_SETTLED"});
        else runs.push(verified);
      } catch (error) {
        // Preserve explicit gap vs played0. Do not echo private keys or answer bodies into logs.
        pending.push({sessionId: session.sessionId, reason: error.code || "SERVER_GAP"});
      }
    }
    after = page.lastEntryId;
    if (!page.hasMore) { terminal = true; pageCount++; break; }
  }
  if (!terminal || pending.length) return {complete: false, reason: terminal ? "PENDING_SERVER_SESSIONS" : "SCAN_BUDGET",
    scannedSessions: seenSessions.size, pagesRead: pageCount, pendingCount: pending.length, pending: pending.slice(0, 100),
    pendingDetailsOmitted: Math.max(0, pending.length - 100), needsLargerBudget: !terminal};
  const ranking = {...buildQualificationRankingSnapshot({eventId, forms: event.qualificationForms, endsAtMs: fence.endsAtMs,
    snapshotId: fence.freezeId, verifiedRuns: runs, completeThroughMs: fence.endsAtMs, pendingSessionCount: 0}),
    freezeFenceId: fence.freezeId, frozenAtMs: now, scannedSessionCount: seenSessions.size, pagesRead: pageCount};
  return db.runTransaction(async transaction => {
    const ref = db.doc(`${QUALIFICATION_RANKINGS}/${eventId}`);
    const [current, prior] = await transaction.getAll(db.doc(`${TOURNAMENT_PATHS.events}/${eventId}`), ref);
    if (!current.exists || current.data().qualificationFreezeFence?.freezeId !== fence.freezeId ||
        current.data().lastMutationAtMs < fence.endsAtMs || formsFingerprint(current.data().qualificationForms) !== fence.formsFingerprint) {
      fail("UNAVAILABLE", "Admission fence changed before ranking publication");
    }
    if (prior.exists) {
      frozenGate(current.data(), prior.data());
      if (prior.data().snapshotId !== fence.freezeId || prior.data().verifiedRunsFingerprint !== ranking.verifiedRunsFingerprint) fail("UNAVAILABLE", "Conflicting frozen qualification results");
      return {complete: true, replayed: true, ranking: prior.data()};
    }
    transaction.create(ref, ranking);
    return {complete: true, replayed: false, ranking};
  });
}

/** Transaction-read dependency for tournament-service.CLOSE_QUALIFICATION. */
async function loadQualificationRanking({db, transaction, eventId, forms, endsAtMs}) {
  const snapshot = await transaction.get(db.doc(`${QUALIFICATION_RANKINGS}/${eventId}`));
  if (!snapshot.exists) return null;
  const ranking = snapshot.data();
  if (ranking.eventId !== eventId || ranking.endsAtMs !== endsAtMs || ranking.formsFingerprint !== formsFingerprint(forms)) fail("UNAVAILABLE", "Frozen ranking belongs to another event/form cutoff");
  return ranking;
}

module.exports = {QUALIFICATION_RANKINGS, fenceQualification, readQualificationEntryPage, freezeQualification, loadQualificationRanking};
