"use strict";

// Minimal query/fault wrapper around the canonical transaction fake. Large cases deliberately
// start at a seeded complete frozen scan; production VALIDATE still re-verifies every source.
const assert = require("node:assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {tournamentDigest: digest} = require("./tournament-bank");
const {computePercentScore} = require("./assessment-scoring");
const {TOURNAMENT_PATHS: P, qualificationSessionId} = require("./tournament-service");
const {VERIFIED_RUNS, VERIFICATION_VERSION} = require("./tournament-session");
const {selectQualificationCalculation, QUALIFICATION_RANKINGS} = require("./tournament-qualification-freeze");
const {scanPath, advanceQualificationScan} = require("./tournament-qualification-scan");
const {calculationPath, advanceQualificationCalculation} = require("./tournament-qualification-calculation");

const T = 1_800_000_000_000; const END = T + 20_000; const EVENT = "event_calculation_000001";
const E = `${P.events}/${EVENT}`; const R = `${QUALIFICATION_RANKINGS}/${EVENT}`;
const cmp = (a, b) => Buffer.compare(Buffer.from(a), Buffer.from(b));
const uidAt = n => `player_${String(n).padStart(5, "0")}`;
const rowKey = entryId => digest(["admission", entryId]);
const clone = value => structuredClone(value);
const compactRun = run => Object.fromEntries(["eventId", "uid", "sessionId", "formId", "formFingerprint", "status",
  "verificationVersion", "percentScore", "elapsedMs", "startedAtMs", "completedAtMs"].map(key => [key, run[key]]));

async function calculationFixture(playerCount = 2, rounds = 1, {realScan = false} = {}) {
  const forms = [1, 2, 3].map(n => ({formId: `qualification-form-${n}`, fingerprint: digest(["form", n]), maxDurationMs: 20_000}));
  const initial = {[E]: {schemaVersion: 1, eventId: EVENT, organizerUid: "organizer", status: "QUALIFICATION", lastMutationAtMs: T,
    source: {kind: "COURSE", id: "course", snapshotId: "immutable-v1"}, poolFingerprint: digest("pool"), privateBankFingerprint: digest("private"), timingVersion: "time-v1",
    qualification: {startsAtMs: T, endsAtMs: END, roundCount: 3, questionsPerRound: 20, attemptsPerRound: 1}, qualificationForms: forms,
    fundedBoxes: 100, escrow: {reservedBoxes: 100, remainingBoxes: 100, paidBoxes: 0, returnedBoxes: 0}},
  "users/organizer": {boxCount: 19, gold: 17}, "currency_ledger/sentinel": {immutableSentinel: true}};
  for (let n = 0; n < playerCount; n++) for (let round = 1; round <= rounds; round++) {
    const uid = uidAt(n); const attemptId = `attempt_${uid}_${round}_00001`; const sessionId = qualificationSessionId(attemptId);
    const form = forms[round - 1]; const elapsedMs = 1 + n % 19_999; const codeAnswer = String(1 + n % 9).repeat(20);
    const common = {schemaVersion: 1, sessionId, uid, eventId: EVENT, kind: "QUALIFICATION", roundNumber: round,
      formId: form.formId, formFingerprint: form.fingerprint, status: "SETTLED", startedAtMs: T, completedAtMs: T + elapsedMs};
    initial[`${P.sessions}/${sessionId}`] = {...common, questionIndex: 20, endsAtMs: END, scoreDigits: codeAnswer};
    initial[`${VERIFIED_RUNS}/${sessionId}`] = {...common, verificationVersion: VERIFICATION_VERSION, servedQuestionCount: 20,
      codeAnswer, percentScore: computePercentScore(codeAnswer), elapsedMs};
    initial[`${E}/entries/${digest([uid, round])}`] = {eventId: EVENT, uid, roundNumber: round, attemptId, sessionId,
      formId: form.formId, formFingerprint: form.fingerprint, startedAtMs: T, status: "STARTED"};
  }
  const db = premiumTestDb(initial); const originalRun = db.runTransaction;
  const controls = {now: END, beforeTransaction: null, beforeRead: null, beforeCommit: null, repeatCallbacks: false};
  const metrics = {reads: 0, maxTransactionReads: 0, maxTransactionWrites: 0, queries: [], transactions: 0};
  let serial = Promise.resolve();
  function query(path, filters = [], after = null, maximum = null) {
    return {calculationQuery: true, path, filters, after, maximum,
      where: (field, op, value) => query(path, [...filters, {field, op, value}], after, maximum),
      orderBy: field => { assert.equal(field, "__name__"); return query(path, filters, after, maximum); },
      startAfter: cursor => query(path, filters, cursor, maximum), limit: limit => query(path, filters, after, limit),
      doc: id => db.doc(`${path}/${id}`), get: () => readQuery({path, filters, after, maximum})};
  }
  function readQuery(q) {
    assert.ok(Number.isSafeInteger(q.maximum) && q.maximum >= 1 && q.maximum <= 101);
    assert.ok(q.path.startsWith(`${E}/`));
    metrics.queries.push(clone({path: q.path, filters: q.filters, after: q.after, maximum: q.maximum}));
    const depth = q.path.split("/").length + 1;
    const matches = [...db.store].filter(([path, row]) => path.startsWith(`${q.path}/`) && path.split("/").length === depth &&
      (q.after === null || cmp(path.split("/").at(-1), q.after) > 0) && q.filters.every(f => f.op === "==" && row[f.field] === f.value))
      .sort(([a], [b]) => cmp(a, b)).slice(0, q.maximum);
    return {docs: matches.map(([path, row]) => ({id: path.split("/").at(-1), exists: true, data: () => clone(row)}))};
  }
  db.collection = path => query(path);
  db.runTransaction = body => {
    const run = async () => {
      if (controls.beforeTransaction) await controls.beforeTransaction();
      return originalRun(async base => {
        metrics.transactions++; let readCount = 0;
        const execute = async dry => {
          const writes = []; let wrote = false;
          const beforeRead = async ref => { assert.equal(wrote, false); readCount++; if (controls.beforeRead) await controls.beforeRead(ref.path, ref); };
          const tx = {getAll: async (...refs) => {
            for (const ref of refs) await beforeRead(ref);
            return base.getAll(...refs);
          }, get: async ref => {
            await beforeRead(ref);
            if (!ref.calculationQuery) return base.get(ref);
            const result = readQuery(ref);
            // Count returned documents (including lookahead), not one read per query call.
            // Empty queries retain one read. This is a fake document-read count, not billing.
            readCount += Math.max(1, result.docs.length) - 1;
            return result;
          }, set: (ref, value, opts) => {
            wrote = true; writes.push({kind: "set", path: ref.path, value: clone(value)}); if (!dry) base.set(ref, value, opts);
          }, create: (ref, value) => {
            wrote = true; writes.push({kind: "create", path: ref.path, value: clone(value)}); if (!dry) base.create(ref, value);
          }};
          const result = await body(tx);
          if (!dry) {
            metrics.maxTransactionWrites = Math.max(metrics.maxTransactionWrites, writes.length);
            if (controls.beforeCommit) await controls.beforeCommit(writes);
          }
          return result;
        };
        if (controls.repeatCallbacks) await execute(true);
        const result = await execute(false);
        metrics.reads += readCount; metrics.maxTransactionReads = Math.max(metrics.maxTransactionReads, readCount);
        return result;
      });
    };
    const result = serial.then(run, run); serial = result.catch(() => {}); return result;
  };
  const selection = await selectQualificationCalculation({db, eventId: EVENT, now: END});
  const contract = selection.contract; const scan = scanPath(EVENT, contract.freezeId); const job = calculationPath(EVENT, contract.freezeId);
  if (realScan) {
    for (let calls = 0; ; calls++) {
      assert.ok(calls < 100, "real scan fixture terminates");
      const result = await advanceQualificationScan({db, eventId: EVENT, now: END, pageSize: 50, maxPages: 1, nowMs: () => controls.now});
      if (result.scanComplete) break;
    }
  } else {
    const anchor = {schemaVersion: 1, eventId: EVENT, freezeId: contract.freezeId, endsAtMs: END,
      formsFingerprint: contract.formsFingerprint, sourceFingerprint: contract.sourceFingerprint,
      algorithmVersion: contract.versions.algorithmFamily, calculationContractDigest: contract.contractDigest};
    const identityHash = digest(anchor); let chainHash = digest(["qualification-scan", anchor]); let after = null;
    const entries = [...db.store].filter(([path]) => path.startsWith(`${E}/entries/`)).sort(([a], [b]) => cmp(a, b));
    const chunkCount = Math.max(1, Math.ceil(entries.length / 50));
    for (let index = 0; index < chunkCount; index++) {
      const page = entries.slice(index * 50, (index + 1) * 50);
      const members = page.map(([path, entry]) => ({entryId: path.split("/").at(-1), entryDigest: digest(entry)}));
      const chunk = {schemaVersion: 1, identityHash, index, afterEntryId: after,
        lastEntryId: members.at(-1)?.entryId ?? after, terminal: index === chunkCount - 1, entries: members};
      const chunkHash = digest(chunk);
      db.store.set(`${scan}/chunks/${String(index).padStart(16, "0")}`, {...chunk, chunkHash, previousChainHash: chainHash});
      chainHash = digest([chainHash, chunkHash]); after = chunk.lastEntryId;
      for (const [path, entry] of page) {
        const entryId = path.split("/").at(-1); const run = compactRun(db.store.get(`${VERIFIED_RUNS}/${entry.sessionId}`));
        const binding = Object.fromEntries(["eventId", "uid", "roundNumber", "attemptId", "sessionId", "formId", "formFingerprint"].map(k => [k, entry[k]]));
        db.store.set(`${scan}/rows/${rowKey(entryId)}`, {schemaVersion: 1, identityHash, entryId, entryDigest: digest(entry), binding,
          status: "VERIFIED", reason: null, run, runDigest: digest(run)});
      }
    }
    db.store.set(scan, {...anchor, identityHash, status: "SCAN_COMPLETE", revision: 1, afterEntryId: after,
      discoveryDone: true, chunkCount, admittedCount: entries.length, verifiedCount: entries.length, pendingCount: 0,
      repairAfterRowId: null, chainHash,
      manifestDigest: digest({identityHash, chainHash, chunkCount, admittedCount: entries.length, verifiedCount: entries.length})});
  }
  const advance = extra => advanceQualificationCalculation({db, eventId: EVENT, now: END, nowMs: () => controls.now,
    deadlineAtMs: controls.now + 120_000, pageSize: 50, maxPages: 1, ...extra});
  const state = () => db.store.get(job);
  async function drain(extra) {
    for (let calls = 1; calls <= 1000; calls++) {
      const result = await advance(extra);
      if (result.complete) return {result, calls};
    }
    throw new Error("Bounded fixture continuation did not finish");
  }
  async function reach(phase) {
    if (phase === "VALIDATE" && !state()) {
      let failed = false;
      controls.beforeCommit = writes => { if (!failed && writes.some(w => w.path.startsWith(`${job}/players/`))) { failed = true; db.failNextCommit(); } };
      await assert.rejects(advance()); controls.beforeCommit = null;
    }
    for (let calls = 0; state()?.phase !== phase; calls++) {
      assert.ok(calls < 1000 && state()?.phase !== "PUBLISHED", `reach ${phase}`); await advance();
    }
  }
  const frozen = [...db.store].map(([path, value]) => [path, digest(value)]);
  return {db, controls, metrics, forms, contract, scan, job, frozen, advance, state, drain, reach,
    ranking: () => db.store.get(R), playerDocuments: () => [...db.store].filter(([path]) => path.startsWith(`${job}/players/`)),
    scanRows: () => [...db.store].filter(([path]) => path.startsWith(`${scan}/rows/`))};
}

module.exports = {calculationFixture, T, END, EVENT, E, R, uidAt};
