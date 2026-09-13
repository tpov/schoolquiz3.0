"use strict";

// Server-only bounded continuation of one COMPLETE, frozen qualification scan. Every cursor,
// reduction and projection is committed with a revision and private invocation lease. No client
// controls its versions, input, time, lease or publication; no prizes are paid by this module.
const {randomUUID} = require("node:crypto");
const {TOURNAMENT_PATHS: P} = require("./tournament-service");
const {tournamentDigest: digest} = require("./tournament-bank");
const {QUALIFICATION_RANKINGS} = require("./tournament-qualification-freeze");
const {validateCalculationContract, validateRankingVersion, assertCurrentCalculationRuntime} = require("./tournament-qualification-version");
const {SCAN_VERSION, scanPath, qualificationCalculationManifest, readQualificationCalculationChunk,
  verifyQualificationCalculationPlayer} = require("./tournament-qualification-scan");
const compact = require("./tournament-qualification-compact");

const JOB_VERSION = 1;
const LEASE_MS = 60_000;
const PHASES = ["VALIDATE", "REDUCE", "SOLVE", "PROJECT", "PUBLISH", "PUBLISHED"];
const data = snap => snap.exists ? snap.data() : null;
const eventPath = id => `${P.events}/${id}`;
const calculationPath = (eventId, freezeId) => `${eventPath(eventId)}/qualification_calculations/${freezeId}`;
function fail(message, code = "UNAVAILABLE") { const e = new Error(message); e.code = code; throw e; }
function exact(n, min = 0) { if (!Number.isSafeInteger(n) || n < min) fail("Invalid exact calculation counter"); return n; }
const add = (a, b) => exact(a + b);
function seal(body) { return {...body, checkpointDigest: digest(body)}; }
function checked(raw, identity) {
  if (!raw) fail("Calculation job is absent");
  const {checkpointDigest, ...body} = raw;
  if (checkpointDigest !== digest(body) || raw.schemaVersion !== JOB_VERSION ||
      raw.identityHash !== digest(identity) || digest(raw.identity) !== digest(identity) ||
      !PHASES.includes(raw.phase)) fail("Calculation identity/checkpoint changed");
  for (const value of [raw.revision, raw.nextChunk, raw.verifiedRuns, raw.playerCount]) exact(value);
  if (raw.nextChunk > identity.chunkCount || raw.verifiedRuns > identity.verifiedCount ||
      raw.playerCount > raw.verifiedRuns) fail("Calculation counters exceed frozen input");
  if (raw.lease !== null && (!raw.lease || typeof raw.lease.token !== "string" || !raw.lease.token)) fail("Invalid calculation lease");
  if (raw.lease) exact(raw.lease.expiresAtMs);
  return raw;
}
function contractFor(event) {
  const contract = validateCalculationContract(event);
  if (!contract) fail("Versioned calculation must be selected before execution");
  return contract;
}
function inputIdentity(event, manifest) {
  const contract = contractFor(event);
  return {eventId: event.eventId, freezeId: contract.freezeId, calculationContractDigest: contract.contractDigest,
    scanIdentityHash: manifest.head.identityHash, scanManifestDigest: manifest.head.manifestDigest,
    scanRevision: manifest.head.revision, chunkCount: manifest.head.chunkCount,
    admittedCount: manifest.head.admittedCount, verifiedCount: manifest.head.verifiedCount};
}
function publicationGate(event, ranking) {
  validateRankingVersion(ranking, event);
  if (ranking?.complete !== true || ranking.eventId !== event.eventId ||
      ranking.freezeFenceId !== event.qualificationFreezeFence?.freezeId || ranking.snapshotId !== ranking.freezeFenceId ||
      ranking.endsAtMs !== event.qualification.endsAtMs ||
      ranking.formsFingerprint !== digest(event.qualificationForms.map(({formId, fingerprint}) => ({formId, fingerprint}))) ||
      !/^[a-f0-9]{64}$/.test(ranking.verifiedRunsFingerprint || "") ||
      !Number.isSafeInteger(ranking.verifiedRunCount) || !Number.isSafeInteger(ranking.totalRanked) ||
      ranking.verifiedRunCount < ranking.totalRanked || ranking.totalRanked < 0 || !Array.isArray(ranking.ranking) ||
      ranking.ranking.length !== Math.min(16, ranking.totalRanked) ||
      new Set(ranking.ranking.map(row => row.uid)).size !== ranking.ranking.length ||
      ranking.ranking.some((row, i) => typeof row.uid !== "string" || !row.uid || row.uid.length > 128 ||
        row.uid.includes("/") || row.place !== i + 1)) {
    fail("Existing ranking is not the complete frozen input");
  }
  return ranking;
}
async function acquire({db, eventId, nowMs, deadlineAtMs, token}) {
  return db.runTransaction(async tx => {
    const [eventSnap, rankingSnap] = await tx.getAll(db.doc(eventPath(eventId)), db.doc(`${QUALIFICATION_RANKINGS}/${eventId}`));
    const event = data(eventSnap);
    if (!event) fail("Event not found", "NOT_FOUND");
    if (rankingSnap.exists) return {ranking: publicationGate(event, data(rankingSnap))};
    if (event.status !== "QUALIFICATION") fail("Unpublished calculation requires an open qualification lifecycle");
    const contract = contractFor(event);
    assertCurrentCalculationRuntime(contract);
    const path = calculationPath(eventId, contract.freezeId);
    const [scanSnap, jobSnap] = await tx.getAll(db.doc(scanPath(eventId, contract.freezeId)), db.doc(path));
    const manifest = qualificationCalculationManifest(event, data(scanSnap));
    const identity = inputIdentity(event, manifest);
    const at = exact(nowMs());
    if (at >= deadlineAtMs) return {waiting: "CALCULATION_TIME_PENDING"};
    if (manifest.head.status !== "SCAN_COMPLETE") fail("Published scan has no ranking");
    let head = jobSnap.exists ? checked(data(jobSnap), identity) : seal({schemaVersion: JOB_VERSION,
      identity, identityHash: digest(identity), phase: "VALIDATE", revision: 0, lease: null,
      nextChunk: 0, afterEntryId: null, chainHash: manifest.initialChainHash, verifiedRuns: 0,
      playerCount: 0, reduction: null, solution: null, projection: null});
    if (head.phase === "PUBLISHED") fail("Published calculation has no ranking");
    if (head.lease && head.lease.expiresAtMs > at) return {waiting: "CALCULATION_LEASE_HELD"};
    const {checkpointDigest, ...body} = head;
    head = seal({...body, revision: add(head.revision, 1), lease: {token, expiresAtMs: add(at, LEASE_MS)}});
    if (jobSnap.exists) tx.set(db.doc(path), head); else tx.create(db.doc(path), head);
    return {identity, path, revision: head.revision};
  });
}
async function readBound(tx, db, context, token, expectedRevision, nowMs, deadlineAtMs) {
  const {identity, path} = context;
  const [eventSnap, scanSnap, jobSnap, rankingSnap] = await tx.getAll(db.doc(eventPath(identity.eventId)),
    db.doc(scanPath(identity.eventId, identity.freezeId)), db.doc(path), db.doc(`${QUALIFICATION_RANKINGS}/${identity.eventId}`));
  const event = data(eventSnap);
  const head = checked(data(jobSnap), identity);
  const contract = contractFor(event);
  const manifest = qualificationCalculationManifest(event, data(scanSnap));
  if (rankingSnap.exists) return {published: publicationGate(event, data(rankingSnap))};
  assertCurrentCalculationRuntime(contract);
  if (digest(inputIdentity(event, manifest)) !== digest(identity) || contract.freezeId !== identity.freezeId) fail("Calculation source changed");
  const at = exact(nowMs());
  if (head.lease?.token !== token || head.revision !== expectedRevision || head.lease.expiresAtMs <= at) {
    fail("Calculation worker lease/revision is stale", "CALCULATION_LEASE_LOST");
  }
  if (at >= deadlineAtMs) fail("Invocation budget reached", "CALCULATION_TIME_PENDING");
  return {event, manifest, head};
}
function playerDocument(raw, identityHash, expectedId) {
  if (!raw || raw.schemaVersion !== 1 || raw.identityHash !== identityHash || raw.playerDigest !== digest(raw.player) ||
      compact.playerCursorKey(raw.player?.userId) !== expectedId || !Array.isArray(raw.player.results) ||
      !raw.player.results.length || raw.player.results.length > 3 || !Array.isArray(raw.admissions) ||
      raw.admissions.length !== raw.player.results.length || raw.admissionsDigest !== digest(raw.admissions)) fail("Invalid immutable player projection");
  return raw;
}
async function validateChunk({db, tx, context, head, event, manifest, now}) {
  const chunk = await readQualificationCalculationChunk({db, transaction: tx, event, rawHead: manifest.head,
    index: head.nextChunk, afterEntryId: head.afterEntryId, chainHash: head.chainHash, now});
  const groups = new Map();
  for (const item of chunk.accepted) {
    const key = compact.playerCursorKey(item.run.uid);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(item);
  }
  const keys = [...groups.keys()];
  const playerRefs = keys.map(key => db.doc(`${context.path}/players/${key}`));
  const sessionRefs = chunk.accepted.map(item => db.doc(`${context.path}/sessions/${item.run.sessionId}`));
  if (new Set(sessionRefs.map(ref => ref.path)).size !== sessionRefs.length) fail("Repeated session in admitted chunk");
  const existing = playerRefs.length ? await tx.getAll(...playerRefs, ...sessionRefs) : [];
  if (existing.slice(playerRefs.length).some(snap => snap.exists)) fail("Repeated session across admitted chunks");
  let playerCount = head.playerCount;
  const players = keys.map((key, i) => {
    const prior = data(existing[i]);
    if (prior) playerDocument(prior, head.identityHash, key);
    else playerCount = add(playerCount, 1);
    const player = prior ? structuredClone(prior.player) : {userId: groups.get(key)[0].run.uid, results: []};
    const admissions = prior ? structuredClone(prior.admissions) : [];
    for (const {entryId, entryDigest, runDigest, run} of groups.get(key)) {
      if (player.results.some(result => result.formId === run.formId)) fail("Repeated player/form in full manifest");
      const form = event.qualificationForms.find(form => form.formId === run.formId);
      if (!form || run.elapsedMs > form.maxDurationMs || run.completedAtMs - run.startedAtMs !== run.elapsedMs) fail("Invalid verified result timing");
      player.results.push({formId: run.formId, percent: run.percentScore, elapsedMs: run.elapsedMs, completedAtMs: run.completedAtMs});
      admissions.push({entryId, entryDigest, runDigest, sessionId: run.sessionId});
    }
    player.results.sort((a, b) => a.formId < b.formId ? -1 : a.formId > b.formId ? 1 : 0);
    admissions.sort((a, b) => a.entryId < b.entryId ? -1 : a.entryId > b.entryId ? 1 : 0);
    return {schemaVersion: 1, identityHash: head.identityHash, player, playerDigest: digest(player),
      admissions, admissionsDigest: digest(admissions)};
  });
  const next = {...head, nextChunk: add(head.nextChunk, 1), afterEntryId: chunk.afterEntryId,
    chainHash: chunk.chainHash, verifiedRuns: add(head.verifiedRuns, chunk.accepted.length), playerCount};
  if (chunk.terminal) {
    if (next.nextChunk !== manifest.head.chunkCount || next.chainHash !== manifest.head.chainHash ||
        next.verifiedRuns !== manifest.head.verifiedCount || next.verifiedRuns !== manifest.head.admittedCount) fail("Calculation did not validate the full manifest");
    next.phase = "REDUCE";
    next.reduction = compact.createReduction({formIds: event.qualificationForms.map(form => form.formId),
      cohortFingerprint: manifest.head.manifestDigest, expectedPlayers: playerCount, expectedRuns: next.verifiedRuns});
  }
  // The caller performs its final deadline/lease check before applying these writes.
  return {next, writes: tx => {
    players.forEach((player, i) => tx.set(playerRefs[i], player));
    chunk.accepted.forEach((item, i) => tx.create(sessionRefs[i], {identityHash: head.identityHash,
      entryId: item.entryId, runDigest: item.runDigest}));
  }};
}
async function playerPage({db, tx, context, head, event, manifest, pageSize}) {
  const checkpoint = head.phase === "REDUCE" ? head.reduction : head.projection;
  const cursor = checkpoint.afterUserId === null ? null : compact.playerCursorKey(checkpoint.afterUserId);
  let query = db.collection(`${context.path}/players`).orderBy("__name__");
  if (cursor !== null) query = query.startAfter(cursor);
  const docs = (await tx.get(query.limit(pageSize + 1))).docs;
  if (!Array.isArray(docs) || docs.length > pageSize + 1) fail("Invalid bounded player page");
  let previous = cursor;
  const page = [];
  for (const doc of docs.slice(0, pageSize)) {
    if (previous !== null && doc.id <= previous) fail("Player cursor did not advance");
    previous = doc.id;
    const stored = playerDocument(doc.data(), head.identityHash, doc.id);
    await verifyQualificationCalculationPlayer({db, transaction: tx, event, rawHead: manifest.head,
      admissions: stored.admissions, player: stored.player});
    page.push(stored.player);
  }
  const next = {...head};
  if (head.phase === "REDUCE") {
    next.reduction = page.length ? compact.reducePage(checkpoint, page) : checkpoint;
    if (docs.length <= pageSize) next.phase = "SOLVE";
  } else {
    next.projection = page.length ? compact.mergeTop16(checkpoint, head.solution, page) : checkpoint;
    if (docs.length <= pageSize) { compact.finishTop16(next.projection); next.phase = "PUBLISH"; }
  }
  return {next, writes: () => {}};
}
async function step({db, context, token, expectedRevision, now, nowMs, deadlineAtMs, pageSize}) {
  return db.runTransaction(async tx => {
    const bound = await readBound(tx, db, context, token, expectedRevision, nowMs, deadlineAtMs);
    if (bound.published) return {complete: true, replayed: true, ranking: bound.published};
    const {head, event, manifest} = bound;
    let plan;
    if (head.phase === "VALIDATE") plan = await validateChunk({db, tx, context, head, event, manifest, now});
    else if (head.phase === "REDUCE" || head.phase === "PROJECT") plan = await playerPage({db, tx, context, head, event, manifest, pageSize});
    else if (head.phase === "SOLVE") {
      const solution = compact.solve(head.reduction);
      plan = {next: {...head, phase: "PROJECT", solution, projection: compact.createTop16(solution)}, writes: () => {}};
    } else if (head.phase === "PUBLISH") {
      const result = compact.finishTop16(head.projection);
      if (result.verifiedRunCount !== head.verifiedRuns || head.nextChunk !== manifest.head.chunkCount ||
          head.chainHash !== manifest.head.chainHash || result.calculationDigest !== head.solution.checkpointDigest) fail("Full calculation publication proof disagrees");
      const contract = contractFor(event);
      const ranking = {schemaVersion: 2, eventId: event.eventId, snapshotId: contract.freezeId, complete: true,
        endsAtMs: contract.endsAtMs, algorithmVersion: compact.VERSIONS.algorithmFamily,
        calculationContractDigest: contract.contractDigest, calculationVersions: {...compact.VERSIONS},
        inputFingerprintVersion: "player-stream-chain-v1", formsFingerprint: contract.formsFingerprint,
        verifiedRunsFingerprint: result.inputFingerprint, verifiedRunCount: result.verifiedRunCount,
        totalRanked: result.totalRanked, ranking: result.ranking, freezeFenceId: contract.freezeId, frozenAtMs: now,
        scannedSessionCount: head.verifiedRuns, pagesRead: head.nextChunk,
        scanManifestVersion: SCAN_VERSION, scanManifestDigest: manifest.head.manifestDigest,
        calcCheckpointDigest: head.projection.checkpointDigest};
      publicationGate(event, ranking);
      const at = exact(nowMs());
      if (at >= deadlineAtMs || at >= head.lease.expiresAtMs) fail("Publication budget reached", "CALCULATION_TIME_PENDING");
      const {checkpointDigest, ...body} = head;
      tx.create(db.doc(`${QUALIFICATION_RANKINGS}/${event.eventId}`), ranking);
      tx.set(db.doc(context.path), seal({...body, phase: "PUBLISHED", revision: add(head.revision, 1), lease: null}));
      tx.set(db.doc(manifest.path), {...manifest.head, status: "PUBLISHED", revision: add(manifest.head.revision, 1)});
      return {complete: true, replayed: false, ranking};
    } else fail("Invalid unfinished calculation phase");
    const at = exact(nowMs());
    if (at >= deadlineAtMs || at >= head.lease.expiresAtMs) fail("Calculation page budget reached", "CALCULATION_TIME_PENDING");
    const {checkpointDigest, ...body} = plan.next;
    const next = seal({...body, revision: add(head.revision, 1), lease: {token, expiresAtMs: add(at, LEASE_MS)}});
    plan.writes(tx);
    tx.set(db.doc(context.path), next);
    return {complete: false, status: "CALCULATION_PROGRESS", phase: next.phase, revision: next.revision};
  });
}
async function release(db, context, token) {
  await db.runTransaction(async tx => {
    const ref = db.doc(context.path);
    const head = checked(data(await tx.get(ref)), context.identity);
    if (head.lease?.token !== token) return;
    const {checkpointDigest, ...body} = head;
    tx.set(ref, seal({...body, lease: null, revision: add(head.revision, 1)}));
  });
}

async function advanceQualificationCalculation({db, eventId, now, nowMs = Date.now,
  deadlineAtMs = Number.MAX_SAFE_INTEGER, pageSize = 50, maxPages = 1}) {
  if (typeof eventId !== "string" || !/^[A-Za-z0-9_-]{16,128}$/.test(eventId) || typeof nowMs !== "function") fail("Invalid trusted calculation input", "INVALID_ARGUMENT");
  exact(now); exact(deadlineAtMs); exact(pageSize, 1); exact(maxPages, 1);
  if (pageSize > 50 || maxPages > 10) fail("Invalid invocation page budget", "INVALID_ARGUMENT");
  const token = randomUUID();
  const context = await acquire({db, eventId, nowMs, deadlineAtMs, token});
  if (context.ranking) return {complete: true, replayed: true, ranking: context.ranking};
  if (context.waiting) return {complete: false, status: context.waiting};
  let revision = context.revision;
  try {
    let result = {complete: false, status: "CALCULATION_TIME_PENDING"};
    for (let page = 0; page < maxPages && exact(nowMs()) < deadlineAtMs; page++) {
      result = await step({db, context, token, expectedRevision: revision, now, nowMs, deadlineAtMs, pageSize});
      if (result.complete) return result;
      revision = result.revision;
    }
    return result;
  } catch (error) {
    if (["CALCULATION_TIME_PENDING", "CALCULATION_LEASE_LOST"].includes(error.code)) return {complete: false, status: error.code};
    throw error;
  } finally { await release(db, context, token); }
}

module.exports = {JOB_VERSION, LEASE_MS, calculationPath, advanceQualificationCalculation};
