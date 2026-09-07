"use strict";

// Trusted worker phases only. No client dispatcher, financial effects, or per-page ranking.
const {TOURNAMENT_PATHS: P, qualificationSessionId} = require("./tournament-service");
const {fenceQualification, QUALIFICATION_RANKINGS} = require("./tournament-qualification-freeze");
const {advanceTournamentSession, VERIFIED_RUNS} = require("./tournament-session");
const {verifiedTournamentRun} = require("./tournament-session-results");
const {buildQualificationRankingSnapshot} = require("./tournament-qualification");
const {ALGORITHM_VERSION} = require("./tournament-ranking");
const {tournamentDigest: digest} = require("./tournament-bank");

const SCAN_VERSION = 1;
const data = snap => snap.exists ? snap.data() : null;
const eventPath = eventId => `${P.events}/${eventId}`;
const scanPath = (eventId, freezeId) => `${eventPath(eventId)}/qualification_scans/${freezeId}`;
const rowKey = entryId => digest(["admission", entryId]);
const chunkKey = index => String(index).padStart(16, "0");
const compare = (a, b) => Buffer.compare(Buffer.from(a), Buffer.from(b));
const terminalScan = state => ["SCAN_COMPLETE", "PUBLISHED"].includes(state.status);
function fail(message) { const e = new Error(message); e.code = "UNAVAILABLE"; throw e; }
function exact(n, min = 0) { if (!Number.isSafeInteger(n) || n < min) fail("Invalid exact scan counter"); return n; }
function plus(a, b) { return exact(a + b); }
function docId(id) {
  if (typeof id !== "string" || !id || id.includes("/") || [".", ".."].includes(id) || Buffer.byteLength(id) > 1500) fail("Invalid admission cursor");
  return id;
}
function identity(event) {
  const fence = event.qualificationFreezeFence;
  if (!fence || event.lastMutationAtMs < event.qualification.endsAtMs || fence.endsAtMs !== event.qualification.endsAtMs ||
      fence.formsFingerprint !== digest(event.qualificationForms.map(({formId, fingerprint}) => ({formId, fingerprint})))) fail("Immutable admission fence required");
  return {schemaVersion: SCAN_VERSION, eventId: event.eventId, freezeId: fence.freezeId, endsAtMs: fence.endsAtMs,
    formsFingerprint: fence.formsFingerprint, sourceFingerprint: digest({source: event.source, poolFingerprint: event.poolFingerprint,
      privateBankFingerprint: event.privateBankFingerprint ?? null, timingVersion: event.timingVersion ?? null}), algorithmVersion: ALGORITHM_VERSION};
}
function initial(anchor) {
  return {...anchor, identityHash: digest(anchor), status: "SCANNING", revision: 0, afterEntryId: null,
    discoveryDone: false, chunkCount: 0, admittedCount: 0, verifiedCount: 0, pendingCount: 0,
    repairAfterRowId: null, chainHash: digest(["qualification-scan", anchor]), manifestDigest: null};
}
function checked(raw, anchor) {
  if (!raw || raw.identityHash !== digest(anchor) || Object.keys(anchor).some(key => raw[key] !== anchor[key]) ||
      !["SCANNING", "DRAINING", "SCAN_COMPLETE", "PUBLISHED"].includes(raw.status) ||
      typeof raw.discoveryDone !== "boolean" || !/^[a-f0-9]{64}$/.test(raw.chainHash || "")) fail("Scan identity or state requires repair");
  [raw.revision, raw.chunkCount, raw.admittedCount, raw.verifiedCount, raw.pendingCount].forEach(n => exact(n));
  if (raw.verifiedCount + raw.pendingCount !== raw.admittedCount || raw.verifiedCount > raw.admittedCount) fail("Scan counters disagree");
  if (raw.afterEntryId !== null) docId(raw.afterEntryId);
  if (raw.repairAfterRowId !== null && !/^[a-f0-9]{64}$/.test(raw.repairAfterRowId)) fail("Repair cursor requires repair");
  if (terminalScan(raw) && (!raw.discoveryDone || raw.pendingCount || raw.manifestDigest !== manifestDigest(raw))) fail("Incomplete immutable manifest");
  return raw;
}
function manifestDigest(state) {
  return digest({identityHash: state.identityHash, chainHash: state.chainHash, chunkCount: state.chunkCount,
    admittedCount: state.admittedCount, verifiedCount: state.verifiedCount});
}
function refresh(state) {
  const next = {...state, revision: plus(state.revision, 1)};
  next.status = next.discoveryDone ? next.pendingCount ? "DRAINING" : "SCAN_COMPLETE" : "SCANNING";
  if (next.status === "SCAN_COMPLETE") next.manifestDigest = manifestDigest(next);
  return next;
}
function boundAdmission(event, entryId, entry) {
  const form = event.qualificationForms[entry?.roundNumber - 1];
  if (!entry || entry.eventId !== event.eventId || typeof entry.uid !== "string" || !entry.uid || entry.uid.length > 128 || entry.uid.includes("/") ||
      !Number.isInteger(entry.roundNumber) || entry.roundNumber < 1 || entry.roundNumber > 3 ||
      !/^[A-Za-z0-9_-]{16,128}$/.test(entry.attemptId || "") || entryId !== digest([entry.uid, entry.roundNumber]) ||
      entry.sessionId !== qualificationSessionId(entry.attemptId) || !form || entry.formId !== form.formId || entry.formFingerprint !== form.fingerprint) return null;
  return {eventId: entry.eventId, uid: entry.uid, roundNumber: entry.roundNumber, attemptId: entry.attemptId,
    sessionId: entry.sessionId, formId: entry.formId, formFingerprint: entry.formFingerprint};
}
function rankingRun(run) {
  return Object.fromEntries(["eventId", "uid", "sessionId", "formId", "formFingerprint", "status", "verificationVersion",
    "percentScore", "elapsedMs", "startedAtMs", "completedAtMs"].map(key => [key, run[key]]));
}
function verifiedEntry(event, binding, session, run, now) {
  if (!binding || !session || session.kind !== "QUALIFICATION" || session.roundNumber !== binding.roundNumber ||
      session.formId !== binding.formId || session.endsAtMs > event.qualification.endsAtMs ||
      session.startedAtMs < event.qualification.startsAtMs || session.startedAtMs >= event.qualification.endsAtMs) fail("Session admission binding requires repair");
  const verified = verifiedTournamentRun(session, run, binding, now);
  return verified ? rankingRun(verified) : null;
}
async function prepare({db, eventId, now}) {
  const fenced = await fenceQualification({db, eventId, now});
  if (fenced.existingRanking) return {published: fenced.existingRanking};
  const anchor = identity(fenced.event); const path = scanPath(eventId, anchor.freezeId);
  await db.runTransaction(async tx => {
    const [eventSnap, head] = await tx.getAll(db.doc(eventPath(eventId)), db.doc(path));
    if (digest(identity(data(eventSnap))) !== digest(anchor)) fail("Fence identity changed");
    if (head.exists) checked(data(head), anchor);
    else tx.create(db.doc(path), initial(anchor));
  });
  return {anchor, path};
}
async function readState(db, context) { return checked(data(await db.doc(context.path).get()), context.anchor); }

async function discoverPage(db, context, pageSize) {
  return db.runTransaction(async tx => {
    const [eventSnap, headSnap] = await tx.getAll(db.doc(eventPath(context.anchor.eventId)), db.doc(context.path));
    const event = data(eventSnap); const head = checked(data(headSnap), identity(event));
    if (head.identityHash !== digest(context.anchor)) fail("Discovery fence changed");
    if (head.discoveryDone) return {changed: false};
    let query = db.collection(`${eventPath(event.eventId)}/entries`).orderBy("__name__");
    if (head.afterEntryId !== null) query = query.startAfter(head.afterEntryId);
    const snapshots = (await tx.get(query.limit(pageSize + 1))).docs;
    if (!Array.isArray(snapshots) || snapshots.length > pageSize + 1) fail("Malformed bounded admission page");
    const selected = snapshots.slice(0, pageSize); let previous = head.afterEntryId;
    const entries = selected.map(snap => {
      const entryId = docId(snap.id); if (previous !== null && compare(entryId, previous) <= 0) fail("Admission page did not advance"); previous = entryId;
      const entry = snap.data(); return {entryId, entryDigest: digest(entry), binding: boundAdmission(event, entryId, entry)};
    });
    const chunk = {schemaVersion: SCAN_VERSION, identityHash: head.identityHash, index: head.chunkCount,
      afterEntryId: head.afterEntryId, lastEntryId: entries.at(-1)?.entryId ?? head.afterEntryId,
      terminal: snapshots.length <= pageSize, entries: entries.map(({entryId, entryDigest}) => ({entryId, entryDigest}))};
    const chunkHash = digest(chunk); const chunkRef = db.doc(`${context.path}/chunks/${chunkKey(chunk.index)}`);
    const rowRefs = entries.map(entry => db.doc(`${context.path}/rows/${rowKey(entry.entryId)}`));
    const existing = await tx.getAll(chunkRef, ...rowRefs);
    if (existing.some(snap => snap.exists)) fail("Discovery cursor overlaps an immutable admission chunk");
    // Chunk, exact admitted identities, and cursor/counts commit together. Session work follows
    // later, so process death cannot lose admissions or keep rescanning one expensive page.
    tx.create(chunkRef, {...chunk, chunkHash, previousChainHash: head.chainHash});
    entries.forEach((entry, index) => tx.create(rowRefs[index], {schemaVersion: SCAN_VERSION, identityHash: head.identityHash,
      ...entry, status: "PENDING", reason: entry.binding ? "NOT_VERIFIED" : "ADMISSION_BINDING"}));
    tx.set(db.doc(context.path), refresh({...head, afterEntryId: chunk.lastEntryId, discoveryDone: chunk.terminal,
      chunkCount: plus(head.chunkCount, 1), admittedCount: plus(head.admittedCount, entries.length),
      pendingCount: plus(head.pendingCount, entries.length), chainHash: digest([head.chainHash, chunkHash])}));
    return {changed: true, count: entries.length};
  });
}

async function pendingPage(db, context, cursor, pageSize) {
  let query = db.collection(`${context.path}/rows`).where("status", "==", "PENDING").orderBy("__name__");
  if (cursor !== null) query = query.startAfter(cursor);
  const docs = (await query.limit(pageSize).get()).docs;
  if (!Array.isArray(docs) || docs.length > pageSize) fail("Malformed bounded repair page");
  let previous = cursor;
  return docs.map(doc => {
    if (!/^[a-f0-9]{64}$/.test(doc.id) || previous !== null && compare(doc.id, previous) <= 0) fail("Repair cursor did not advance");
    previous = doc.id; return doc.id;
  });
}
async function checkpointRepair(db, context, expected, next) {
  return db.runTransaction(async tx => {
    const head = checked(data(await tx.get(db.doc(context.path))), context.anchor);
    if (terminalScan(head) || head.repairAfterRowId !== expected) return false;
    tx.set(db.doc(context.path), {...head, revision: plus(head.revision, 1), repairAfterRowId: next}); return true;
  });
}
async function repairRow(db, context, key, now, mayContinue) {
  const rowRef = db.doc(`${context.path}/rows/${key}`);
  const before = data(await rowRef.get());
  if (!before || before.identityHash !== digest(context.anchor) || rowKey(docId(before.entryId)) !== key) fail("Repair row identity changed");
  if (before.status === "VERIFIED") return;
  if (!mayContinue()) return {stopped: true};
  const allowedBinding = await db.runTransaction(async tx => {
    const [eventSnap, entrySnap] = await tx.getAll(db.doc(eventPath(context.anchor.eventId)),
      db.doc(`${eventPath(context.anchor.eventId)}/entries/${before.entryId}`));
    const event = data(eventSnap); const entry = data(entrySnap);
    if (digest(identity(event)) !== digest(context.anchor)) fail("Repair fence changed before session work");
    const binding = boundAdmission(event, before.entryId, entry);
    return binding && entry && digest(entry) === before.entryDigest && digest(binding) === digest(before.binding) ? binding : null;
  });
  if (!mayContinue()) return {stopped: true};
  // Finishing a session is idempotent and independent of scan progress. A crash after this call
  // but before the row transaction retries the same permanent session operation, not new work.
  if (allowedBinding) {
    try { await advanceTournamentSession({db, sessionId: allowedBinding.sessionId,
      operationId: `freeze_advance_${digest([context.anchor.freezeId, allowedBinding.sessionId])}`, now}); }
    catch { /* Record a fixed repair reason below; never trust errors as scores or diagnostics. */ }
  }
  return db.runTransaction(async tx => {
    const [headSnap, eventSnap, rowSnap] = await tx.getAll(db.doc(context.path), db.doc(eventPath(context.anchor.eventId)), rowRef);
    const head = checked(data(headSnap), context.anchor); const event = data(eventSnap); const row = data(rowSnap);
    if (digest(identity(event)) !== digest(context.anchor) || !row || row.entryDigest !== before.entryDigest ||
        row.identityHash !== head.identityHash || row.entryId !== before.entryId || rowKey(row.entryId) !== key) fail("Repair row/fence binding changed");
    if (row.status === "VERIFIED") return;
    if (terminalScan(head) || row.status !== "PENDING") fail("Immutable manifest cannot be repaired in place");
    const entry = data(await tx.get(db.doc(`${eventPath(event.eventId)}/entries/${row.entryId}`)));
    const binding = boundAdmission(event, row.entryId, entry);
    let verified = null; let reason = "ADMISSION_BINDING";
    if (entry && digest(entry) === row.entryDigest && binding && digest(binding) === digest(row.binding)) {
      const [session, run] = await tx.getAll(db.doc(`${P.sessions}/${binding.sessionId}`), db.doc(`${VERIFIED_RUNS}/${binding.sessionId}`));
      try { verified = verifiedEntry(event, binding, data(session), data(run), now); reason = "SERVER_SESSION_PENDING"; }
      catch { reason = "SERVER_SESSION_REPAIR"; }
    }
    if (!verified) { tx.set(rowRef, {...row, reason}); return; }
    if (head.pendingCount < 1) fail("Pending admission counter disagrees with its row");
    // Exactly one PENDING→VERIFIED transition per immutable admitted ID. Replays cannot
    // compensate for a missing different entrant by incrementing these counters twice.
    tx.set(rowRef, {...row, status: "VERIFIED", reason: null, run: verified, runDigest: digest(verified)});
    tx.set(db.doc(context.path), refresh({...head, pendingCount: head.pendingCount - 1, verifiedCount: plus(head.verifiedCount, 1)}));
  });
}

/** Bounded discovery/repair only; complete never means a partial ranking was published. */
async function advanceQualificationScan({db, eventId, now, pageSize = 50, maxPages = 1,
  nowMs = Date.now, deadlineAtMs = Number.MAX_SAFE_INTEGER}) {
  exact(now); exact(pageSize, 1); exact(maxPages, 1); exact(deadlineAtMs);
  if (pageSize > 50 || maxPages > 10 || typeof nowMs !== "function") fail("Invalid trusted invocation bounds");
  const mayContinue = () => exact(nowMs()) < deadlineAtMs;
  const context = await prepare({db, eventId, now});
  if (context.published) return {complete: true, status: "PUBLISHED", ranking: context.published};
  let discoveredPages = 0; let repairedAttempts = 0;
  for (; discoveredPages < maxPages && mayContinue(); discoveredPages++) {
    if (!(await discoverPage(db, context, pageSize)).changed) break;
  }
  let head = await readState(db, context); let cursor = head.repairAfterRowId;
  if (!terminalScan(head) && mayContinue()) {
    let keys = await pendingPage(db, context, cursor, pageSize);
    if (!keys.length && cursor !== null && mayContinue() && await checkpointRepair(db, context, cursor, null)) {
      cursor = null; keys = await pendingPage(db, context, null, pageSize);
    }
    for (const key of keys) {
      if (!mayContinue()) break;
      if ((await repairRow(db, context, key, now, mayContinue))?.stopped) break;
      repairedAttempts++;
      if (!await checkpointRepair(db, context, cursor, key)) break;
      cursor = key;
    }
  }
  head = await readState(db, context);
  return {complete: false, status: terminalScan(head) ? "RANKING_PENDING" : head.discoveryDone ? "PENDING_REPAIR" : "SCAN_PROGRESS",
    scanComplete: terminalScan(head), eventId, freezeId: head.freezeId, discoveredPages, repairedAttempts,
    admittedCount: head.admittedCount, verifiedCount: head.verifiedCount, pendingCount: head.pendingCount,
    manifestDigest: head.manifestDigest};
}

/** Separate trusted calculation phase. Reads every immutable admitted identity, not just
 * counters. Full-cohort memory/CPU remains the unchanged LS implementation's resource limit.
 */
class CalculationPending extends Error {
  constructor(status) { super(status); this.status = status; }
}
function calculationBudget({maxVerifiedRuns = Number.MAX_SAFE_INTEGER, nowMs = Date.now,
  deadlineAtMs = Number.MAX_SAFE_INTEGER, minRemainingBeforeSolveMs = 0}) {
  exact(maxVerifiedRuns); exact(deadlineAtMs); exact(minRemainingBeforeSolveMs);
  if (typeof nowMs !== "function") fail("Trusted calculation clock required");
  return (count = 0, beforeSolve = false) => {
    if (count > maxVerifiedRuns) throw new CalculationPending("CALCULATION_RESOURCE_PENDING");
    const remaining = deadlineAtMs - exact(nowMs());
    if (remaining <= 0 || beforeSolve && remaining < minRemainingBeforeSolveMs) throw new CalculationPending("CALCULATION_TIME_PENDING");
  };
}
async function readCompleteQualificationManifest(options) {
  try { return await readManifest(options, calculationBudget(options)); }
  catch (error) {
    if (error instanceof CalculationPending) return {ready: false, status: error.status};
    throw error;
  }
}
async function readManifest({db, eventId, now}, checkBudget) {
  checkBudget();
  const context = await prepare({db, eventId, now});
  if (context.published) return {published: context.published};
  const head = await readState(db, context);
  if (!terminalScan(head)) return {ready: false, status: "SCAN_PENDING"};
  checkBudget(head.admittedCount);
  const entries = new Set(); const sessions = new Set(); const runs = [];
  let chainHash = initial(context.anchor).chainHash; let afterEntryId = null; let terminal = false;
  for (let index = 0; index < head.chunkCount; index++) {
    checkBudget(runs.length);
    await db.runTransaction(async tx => {
      checkBudget(runs.length);
      const [chunkSnap, eventSnap] = await tx.getAll(db.doc(`${context.path}/chunks/${chunkKey(index)}`), db.doc(eventPath(eventId)));
      checkBudget(runs.length);
      const chunk = data(chunkSnap); const event = data(eventSnap);
      if (!chunk || digest(identity(event)) !== digest(context.anchor) || chunk.identityHash !== head.identityHash || chunk.index !== index ||
          chunk.afterEntryId !== afterEntryId || chunk.previousChainHash !== chainHash || terminal ||
          !Array.isArray(chunk.entries) || chunk.entries.length > 50 || typeof chunk.terminal !== "boolean") fail("Incomplete admission chunk chain");
      const {chunkHash, previousChainHash, ...body} = chunk;
      if (chunkHash !== digest(body)) fail("Admission chunk digest mismatch");
      const rowSnaps = chunk.entries.length ? await tx.getAll(...chunk.entries.map(entry => db.doc(`${context.path}/rows/${rowKey(entry.entryId)}`))) : [];
      const accepted = [];
      for (let i = 0; i < chunk.entries.length; i++) {
        checkBudget(runs.length + i + 1);
        const entry = chunk.entries[i]; const row = data(rowSnaps[i]); const id = docId(entry.entryId);
        if (entries.has(id) || afterEntryId !== null && compare(id, afterEntryId) <= 0 || !row || row.status !== "VERIFIED" ||
            row.identityHash !== head.identityHash || row.entryId !== id || row.entryDigest !== entry.entryDigest ||
            !row.binding || sessions.has(row.binding.sessionId) || row.runDigest !== digest(row.run)) fail("Missing, repeated or unverified admitted row");
        const [session, run, savedEntry] = await tx.getAll(db.doc(`${P.sessions}/${row.binding.sessionId}`),
          db.doc(`${VERIFIED_RUNS}/${row.binding.sessionId}`), db.doc(`${eventPath(eventId)}/entries/${id}`));
        checkBudget(runs.length + i + 1);
        const binding = boundAdmission(event, id, data(savedEntry));
        if (!binding || digest(data(savedEntry)) !== row.entryDigest || digest(binding) !== digest(row.binding)) fail("Frozen admission was modified");
        const verified = verifiedEntry(event, binding, data(session), data(run), now);
        if (!verified || digest(verified) !== row.runDigest) fail("Immutable verified receipt was modified");
        // Do not mutate cross-page accumulation inside a retryable transaction callback.
        accepted.push({entryId: id, sessionId: binding.sessionId, run: verified});
      }
      if ((accepted.at(-1)?.entryId ?? afterEntryId) !== chunk.lastEntryId) fail("Admission chunk endpoint disagrees");
      return {accepted, lastEntryId: chunk.lastEntryId, terminal: chunk.terminal, nextChainHash: digest([chainHash, chunkHash])};
    }).then(page => {
      for (const row of page.accepted) {
        if (entries.has(row.entryId) || sessions.has(row.sessionId)) fail("Duplicate manifest membership");
        entries.add(row.entryId); sessions.add(row.sessionId); runs.push(row.run);
      }
      afterEntryId = page.lastEntryId; terminal = page.terminal; chainHash = page.nextChainHash;
    });
  }
  const current = await readState(db, context);
  checkBudget(runs.length);
  if (!terminal || chainHash !== head.chainHash || entries.size !== head.admittedCount || runs.length !== head.verifiedCount ||
      current.manifestDigest !== head.manifestDigest || current.revision !== head.revision) fail("Manifest is not a complete stable cohort");
  const event = data(await db.doc(eventPath(eventId)).get());
  checkBudget(runs.length);
  if (digest(identity(event)) !== digest(context.anchor)) fail("Manifest fence changed");
  return {ready: true, context, head, event, verifiedRuns: runs};
}

async function calculateAndPublishQualificationRanking(options) {
  try { return await calculateAndPublish(options, calculationBudget(options)); }
  catch (error) {
    if (error instanceof CalculationPending) return {complete: false, status: error.status};
    throw error;
  }
}
async function calculateAndPublish(options, checkBudget) {
  const {db, eventId, now} = options;
  exact(now);
  const manifest = await readManifest(options, checkBudget);
  if (manifest.published) return {complete: true, replayed: true, ranking: manifest.published};
  if (!manifest.ready) return {complete: false, status: manifest.status};
  const {event, head, context} = manifest;
  // Full-cohort LS is synchronous and unchanged. This is a cooperative execution envelope,
  // NOT cancellation or an admission cap; an exceeded budget never publishes partial ranks.
  checkBudget(manifest.verifiedRuns.length, true);
  const ranking = {...buildQualificationRankingSnapshot({eventId, forms: event.qualificationForms, endsAtMs: head.endsAtMs,
    snapshotId: head.freezeId, verifiedRuns: manifest.verifiedRuns, completeThroughMs: head.endsAtMs, pendingSessionCount: 0}),
    freezeFenceId: head.freezeId, frozenAtMs: now, scannedSessionCount: head.admittedCount, pagesRead: head.chunkCount,
    scanManifestVersion: SCAN_VERSION, scanManifestDigest: head.manifestDigest};
  checkBudget(manifest.verifiedRuns.length);
  return db.runTransaction(async tx => {
    checkBudget(manifest.verifiedRuns.length);
    const ref = db.doc(`${QUALIFICATION_RANKINGS}/${eventId}`);
    const [eventSnap, currentSnap, priorSnap] = await tx.getAll(db.doc(eventPath(eventId)), db.doc(context.path), ref);
    const current = checked(data(currentSnap), context.anchor);
    if (digest(identity(data(eventSnap))) !== digest(context.anchor) || !terminalScan(current) || current.manifestDigest !== head.manifestDigest) fail("Manifest changed before ranking publication");
    const prior = data(priorSnap);
    if (prior) {
      if (prior.freezeFenceId !== ranking.freezeFenceId || prior.verifiedRunsFingerprint !== ranking.verifiedRunsFingerprint ||
          prior.algorithmVersion !== ranking.algorithmVersion || prior.complete !== true) fail("Conflicting immutable ranking");
      return {complete: true, replayed: true, ranking: prior};
    }
    if (current.revision !== head.revision) fail("Manifest revision changed before publication");
    checkBudget(manifest.verifiedRuns.length);
    tx.create(ref, ranking);
    tx.set(db.doc(context.path), {...current, status: "PUBLISHED", revision: plus(current.revision, 1)});
    return {complete: true, replayed: false, ranking};
  });
}

module.exports = {SCAN_VERSION, scanPath, advanceQualificationScan, readCompleteQualificationManifest, calculateAndPublishQualificationRanking};
