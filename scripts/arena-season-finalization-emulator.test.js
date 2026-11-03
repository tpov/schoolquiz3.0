"use strict";

// Real Admin-SDK proof for the private TZ06D pipeline.  This script intentionally leaves an
// uniquely named demo fixture behind: deleting it would turn a useful audit trail into an
// unrecorded destructive test.  It must run only against an explicitly configured Emulator.

const assert = require("node:assert/strict");
const {randomBytes} = require("node:crypto");
const admin = require("../functions/node_modules/firebase-admin");
const policy = require("../functions/arena-policy");
const {createArenaService, mutationDocumentId, awardDocumentId, LEDGER_PATH} = require("../functions/arena-service");
const {createArenaSeasonWorker} = require("../functions/arena-season-worker");
const {CALCULATION_VERSION} = require("../functions/arena-season-finalization");

function required(value, name) {
  if (typeof value !== "string" || !value.trim()) throw new Error(`${name} is required for this Emulator proof`);
  return value;
}
function fixtureId(prefix, token) { return `${prefix}_${token}`; }
function snapshotData(snapshot) { return snapshot.exists ? snapshot.data() : null; }

async function uniqueQuarter(db) {
  for (let attempt = 0; attempt < 32; attempt++) {
    const bytes = randomBytes(3); const year = 2100 + ((bytes.readUInt16BE(0) + attempt) % 7000);
    const quarterAtMs = Date.UTC(year, (bytes[2] % 4) * 3, 1);
    const quarter = policy.utcQuarterWindow(quarterAtMs);
    if (!(await db.doc(`arena_seasons/${quarter.id}`).get()).exists) return quarter;
  }
  throw new Error("could not reserve a unique future quarter for the TZ06D demo fixture");
}

async function main() {
  required(process.env.FIRESTORE_EMULATOR_HOST, "FIRESTORE_EMULATOR_HOST");
  const projectId = required(process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT, "GCLOUD_PROJECT");
  const app = admin.apps.length ? admin.app() : admin.initializeApp({projectId});
  const db = admin.firestore(app);
  const existing = await db.collection("arena_seasons").where("status", "==", "FINALIZING").limit(1).get();
  if (!existing.empty) throw new Error("refusing to sweep while a pre-existing finalization is active");

  const token = randomBytes(12).toString("hex");
  const quarter = await uniqueQuarter(db);
  const lessonId = fixtureId("tz06d_lesson", token);
  const authorUid = fixtureId("tz06d_author", token);
  const curatorUid = fixtureId("tz06d_curator", token);
  const finalizationMutationId = fixtureId("tz06d_finalize", token);
  const workerStatePath = `arena_season_worker_state/${fixtureId("tz06d", token)}`;
  const clock = {now: quarter.startMs + 1_000};

  await Promise.all([
    db.doc(`server_contexts/${lessonId}`).create({lessonId, authorUid, questType: "REGULAR", published: true,
      publishedAtMs: quarter.startMs - 1, contentVersion: 1, reviewApproved: true}),
    db.doc(`server_roles/${curatorUid}`).create({curator: true}),
    db.doc(`users/${authorUid}`).create({boxCount: 5}),
    db.doc(`arena_seasons/${quarter.id}`).create({quarter, status: "OPEN", createdAtMs: clock.now}),
    db.doc(`arena_seasons/${quarter.id}/members/${lessonId}`).create({lessonId, authorUid, questType: "REGULAR",
      publishedAtMs: quarter.startMs - 1, contentVersion: 1, capturedAtMs: clock.now}),
  ]);

  const service = createArenaService({
    db, policy, nowMs: () => clock.now,
    loadLessonContext: async (tx, id) => snapshotData(await tx.get(db.doc(`server_contexts/${id}`))),
    loadCompletion: async (tx, {attemptId}) => snapshotData(await tx.get(db.doc(`server_results/${attemptId}`))),
    isCurator: async (tx, uid) => snapshotData(await tx.get(db.doc(`server_roles/${uid}`)))?.curator === true,
  });

  for (let index = 0; index < policy.MIN_SEASON_RATERS; index++) {
    const uid = fixtureId(`tz06d_rater_${index}`, token);
    const attemptId = fixtureId(`tz06d_attempt_${index}`, token);
    await db.doc(`server_results/${attemptId}`).create({attemptId, uid, lessonId, status: "SETTLED", completed: true,
      settledAtMs: quarter.startMs, activityKind: "ARENA"});
    const result = await service.submitLessonRating({uid, mutationId: fixtureId(`tz06d_rate_${index}`, token), lessonId, attemptId,
      rating: 3});
    assert.equal(result.status, "APPLIED");
    assert.equal(result.result.seasonId, quarter.id);
    clock.now++;
  }

  clock.now = quarter.endMs;
  const pending = await service.finalizeSeason({uid: curatorUid, mutationId: finalizationMutationId, quarterAtMs: quarter.startMs});
  assert.equal(pending.status, "PENDING");
  const worker = createArenaSeasonWorker({
    db, nowMs: () => clock.now, maxFinalizations: 1, maxAdvancesPerFinalization: 64, statePath: workerStatePath,
    // The scheduler owns only a quarter cursor; the service resolves its current private job ID.
    advanceFinalization: input => service.advanceSeasonFinalizationForQuarter(input), log: () => {},
  });
  const sweep = await worker.sweep();
  assert.equal(sweep.failed, 0); assert.ok(sweep.advanceCalls > 0 && sweep.advanceCalls <= 64);

  const season = snapshotData(await db.doc(`arena_seasons/${quarter.id}`).get());
  assert.equal(season.status, "FINALIZED");
  assert.equal(season.result.winner.lessonId, lessonId);
  assert.equal(season.result.winner.ratingCount, policy.MIN_SEASON_RATERS);
  const jobPath = `arena_seasons/${quarter.id}/finalizations/${pending.finalizationId}`;
  const job = snapshotData(await db.doc(jobPath).get());
  assert.equal(job.calculationVersion, CALCULATION_VERSION);
  assert.match(job.selectionContentHash, /^[a-f0-9]{64}$/); assert.match(job.aggregateContentHash, /^[a-f0-9]{64}$/);
  const [selectionNodes, aggregateNodes] = await Promise.all([
    db.collection(`${jobPath}/selection_tree`).get(), db.collection(`${jobPath}/aggregate_tree`).get(),
  ]);
  for (const [kind, nodes] of [["SELECTION_TREE_NODE", selectionNodes], ["AGGREGATE_TREE_NODE", aggregateNodes]]) {
    assert.ok(nodes.size >= 1); const root = nodes.docs.find(doc => doc.data().depth === 0);
    const edges = JSON.parse(root?.data().childrenJson || "null");
    assert.equal(root?.data().kind, kind); assert.ok(Array.isArray(edges) && edges.length > 0 && edges.length <= 256);
    assert.ok(edges.every(edge => Array.isArray(edge) && (edge[1] === "L" || edge[1] === "N")));
  }
  const receiptId = mutationDocumentId(curatorUid, finalizationMutationId);
  const receipt = snapshotData(await db.doc(`arena_mutations/${receiptId}`).get());
  assert.equal(receipt?.status, "APPLIED");
  assert.equal(receipt?.operation, "FINALIZE_SEASON");
  const awardKey = JSON.stringify(["arena", "season", quarter.id]);
  const awardId = awardDocumentId(awardKey);
  const award = snapshotData(await db.doc(`arena_awards/${awardId}`).get());
  assert.deepEqual({...award, awardedAtMs: 0}, {
    key: awardKey, kind: "SEASON_WIN", lessonId, recipientUid: authorUid, currency: "BOXES", amount: 10, awardedAtMs: 0,
  });
  const ledgerId = `${authorUid}_boxes_arena_season_${awardId}`;
  const ledger = snapshotData(await db.doc(`${LEDGER_PATH}/${ledgerId}`).get());
  assert.equal(ledger?.delta, 10); assert.equal(ledger?.sourceId, awardId); assert.equal(ledger?.reason, "arena_season");
  assert.equal(snapshotData(await db.doc(`users/${authorUid}`).get())?.boxCount, 15);
  const repeat = await worker.sweep();
  assert.equal(repeat.attempted, 0);
  assert.equal((await db.collection("arena_awards").where("key", "==", awardKey).get()).size, 1);

  process.stdout.write(`${JSON.stringify({status: "PASS", fixture: {quarterId: quarter.id, lessonId, finalizationId: pending.finalizationId,
    receiptId, awardId, ledgerId, workerStatePath, calculationVersion: job.calculationVersion,
    selectionTreeNodes: selectionNodes.size, aggregateTreeNodes: aggregateNodes.size},
  sweep: {advanceCalls: sweep.advanceCalls, terminal: sweep.terminal}})}\n`);
}

main().catch(error => {
  process.stderr.write(`TZ06D Emulator proof failed: ${error.stack || error.message}\n`);
  process.exitCode = 1;
});
