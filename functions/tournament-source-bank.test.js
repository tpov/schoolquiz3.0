"use strict";

const assert = require("node:assert/strict");
const {premiumTestDb} = require("./premium-test-db");
const {createTournamentSourceBank, SOURCE_BANKS, PREPARATIONS, TIMING_VERSION} = require("./tournament-source-bank");
const {redact, restoreContent, restoreAnswer} = require("./question-redaction");
const {questionAllocatedSeconds} = require("./lesson-reward");
const {tournamentDigest: digest} = require("./tournament-bank");
const {applyTournamentOperation} = require("./tournament-service");
const {buildQualificationRankingSnapshot} = require("./tournament-qualification");

const T = 1_800_000_000_000;
const uid = "author"; const requestId = "prepare_request_00000001";
const tests = [];
function test(name, fn) { tests.push({name, fn}); }
function content(index, type = "SingleChoice") {
  const base = {id: "untrusted-embedded-id", type, difficulty: index < 60 ? "EASY" : "HARD", text: `Question ${index}`, info: "Private explanation"};
  if (type === "SingleChoice") return {...base, options: [{id: "a", text: "A"}, {id: "b", text: "B"}], correctOptionId: "a"};
  if (type === "MultipleChoice") return {...base, options: [{id: "a", text: "A"}, {id: "b", text: "B"}], correctOptionIds: ["a", "b"]};
  if (type === "Ordering") return {...base, items: [{id: "x", text: "First"}, {id: "y", text: "Second"}, {id: "z", text: "Third"}]};
  if (type === "FillBlank") return {...base, text: "The ___ is ___", blanks: [{id: "b1", correctCandidateId: "x"}, {id: "b2", correctCandidateId: "y"}],
    candidates: [{id: "x", text: "sky"}, {id: "y", text: "blue"}]};
  return base;
}
function fixture({arena = false, limits = {}, types = ["SingleChoice"], reviewApproved = true} = {}) {
  const source = {kind: arena ? "ARENA_LESSON" : "COURSE", id: arena ? "lesson" : "course"};
  const db = premiumTestDb({
    "catalogs/catalog": {questType: arena ? "REGULAR" : "COURSE", version: 1},
    "quests/course": {catalogId: "catalog", authorUid: uid, version: 7, visibleOn: [arena ? "arena" : "archive"], archived: !arena},
    "sections/section": {questId: "course", version: 2, archived: false},
    "themes/theme": {sectionId: "section", version: 3, archived: false},
    "lessons/lesson": {themeId: "theme", version: 4, archived: false, reviewSubmissionId: "submission-v4", reviewApprovedAtMs: T - 100},
    "lesson_placements/lesson": {lessonId: "lesson", authorUid: uid, shelf: "arena", contentVersion: 4, placementVersion: 2},
    "profiles/curator": {curator: true},
    "users/author": {boxCount: 100, standardHearts: 5, goldHearts: 1, lifePoints: 0, plasmaPoints: 0,
      lifePointsUpdatedAtMs: T, plasmaPointsUpdatedAtMs: T},
  });
  for (let i = 0; i < 80; i++) db.store.set(`questions/q${String(i).padStart(3, "0")}`, {lessonId: "lesson", version: 4,
    language: "en", order: i, archived: false, payload: JSON.stringify(content(i, types[i % types.length]))});
  const state = {reviewApproved, contextLoads: 0, curatorLoads: 0};
  const loaders = {
    async loadLessonContext(tx, lessonId) {
      state.contextLoads++;
      const lesson = (await tx.get(db.doc(`lessons/${lessonId}`))).data();
      if (!lesson) return null;
      const theme = (await tx.get(db.doc(`themes/${lesson.themeId}`))).data();
      if (!theme) return null;
      const section = (await tx.get(db.doc(`sections/${theme.sectionId}`))).data();
      if (!section) return null;
      const quest = (await tx.get(db.doc(`quests/${section.questId}`))).data();
      if (!quest) return null;
      const catalog = (await tx.get(db.doc(`catalogs/${quest.catalogId}`))).data();
      return {lessonId, authorUid: quest.authorUid, catalogId: quest.catalogId, questId: section.questId,
        sectionId: theme.sectionId, themeId: lesson.themeId, questType: catalog.questType, contentVersion: lesson.version,
        published: !lesson.archived && !theme.archived && !section.archived && !quest.archived,
        reviewApproved: state.reviewApproved && lesson.reviewSubmissionId === "submission-v4", nextChangeAtMs: 10};
    },
    async isCurator(tx, requestedUid) {
      state.curatorLoads++;
      const profile = (await tx.get(db.doc(`profiles/${requestedUid}`))).data();
      return profile?.curator === true;
    },
  };
  const service = createTournamentSourceBank({db, ...loaders, nowMs: () => T, limits});
  const prepare = (changes = {}, user = uid) => service.prepare({uid: user, payload: {requestId, source, ...changes}});
  const load = (prepared, purpose = "CREATE", user = uid) => db.runTransaction((transaction) => service.loadBank({transaction,
    requesterUid: user, sourceRef: {...source, snapshotId: prepared.snapshotId}, purpose}));
  return {db, source, state, service, loaders, prepare, load};
}
function stored(f, response) { return f.db.store.get(`${SOURCE_BANKS}/${response.snapshotId}`); }
function bank(f, response) { return JSON.parse(stored(f, response).bankJson); }
function change(f, path, patch) { f.db.store.set(path, {...f.db.store.get(path), ...patch}); }
async function error(fn, code) { await assert.rejects(fn, (e) => { assert.equal(e.code, code); return true; }); }

test("archive root is eligible; complete private bank is committed with permanent owner-bound receipt", async () => {
  const f = fixture(); const result = await f.prepare();
  assert.deepEqual(Object.keys(result).sort(), ["counts", "snapshotId", "timingVersion"]);
  assert.deepEqual(result.counts, {easy: 60, hard: 20, total: 80}); assert.equal(result.timingVersion, TIMING_VERSION);
  assert.equal(result.snapshotId, `bank_${digest(requestId)}`);
  assert.equal(stored(f, result).ownerUid, uid);
  assert.equal(f.db.store.get(`${PREPARATIONS}/${digest(requestId)}`).uid, uid);
  assert.equal(f.db.commits[0].length, 2); assert.ok(f.db.commits[0].every((row) => row.op === "create"));
  assert.deepEqual(await f.load(result), bank(f, result));
  assert.ok(!JSON.stringify(result).includes("correctOptionId")); assert.ok(!JSON.stringify(result).includes("Hash"));
});

test("every question uses real document ID in both matching halves, with versioned shared timer", async () => {
  const f = fixture(); const b = bank(f, await f.prepare());
  for (const q of b.questions) {
    const publicQuestion = JSON.parse(q.publicPayload);
    assert.equal(publicQuestion.id, q.questionId); assert.equal(q.answerKey.questionId, q.questionId);
    assert.equal(q.keyContentVersion, q.contentVersion); assert.equal(q.publicHalfRedacted, true);
    assert.equal(q.maxDurationMs, questionAllocatedSeconds(publicQuestion, q.difficulty === "HARD") * 1000);
    assert.ok(!q.publicPayload.includes("correctOptionId")); assert.ok(!q.publicPayload.includes("info"));
  }
});

test("all assessment types restore matching shuffled halves and keep answers private", async () => {
  const f = fixture({types: ["SingleChoice", "MultipleChoice", "Ordering", "FillBlank"]});
  const b = bank(f, await f.prepare());
  for (const q of b.questions) {
    const restored = restoreContent(q.publicPayload, q.answerKey);
    assert.ok(restored); assert.ok(restoreAnswer(q.answerKey));
    assert.equal(restored.id, q.questionId);
    const original = JSON.parse(f.db.store.get(`questions/${q.questionId}`).payload);
    for (const field of ["text", "items", "options", "blanks", "correctOptionId", "correctOptionIds"]) {
      if (original[field] !== undefined) assert.deepEqual(restored[field], original[field]);
    }
    if (original.candidates) assert.deepEqual([...restored.candidates].sort((a, b) => a.id.localeCompare(b.id)), original.candidates);
    assert.ok(!q.publicPayload.includes("correctByBlank"));
  }
});

test("lost prepare response replays immutable original after source retirement without new reads/redaction", async () => {
  const f = fixture(); const response = await f.prepare(); const snapshot = stored(f, response); const loads = f.state.contextLoads;
  change(f, "quests/course", {visibleOn: []}); f.state.reviewApproved = false;
  assert.deepEqual(await f.prepare(), response); assert.deepEqual(stored(f, response), snapshot);
  assert.equal(f.state.contextLoads, loads); assert.equal(f.db.commits.at(-1).length, 0);
});

test("mutated request-ID replay is refused", async () => {
  const f = fixture(); await f.prepare();
  await error(() => f.prepare({source: {kind: "COURSE", id: "other"}}), "PREPARATION_REUSE");
});
test("account switch cannot reuse request ID, snapshot or private bank", async () => {
  const f = fixture(); const response = await f.prepare();
  await error(() => f.prepare({}, "other"), "PREPARATION_REUSE");
  await error(() => f.load(response, "CREATE", "other"), "SOURCE_NOT_ELIGIBLE");
  await error(() => f.load(response, "RECOVERY", "other"), "SOURCE_NOT_ELIGIBLE");
});
test("client ownership/role/clock/snapshot fields are rejected", async () => {
  const f = fixture();
  for (const field of ["ownerUid", "curator", "nowMs", "snapshotId"]) await error(() => f.prepare({[field]: true}), "INVALID_ARGUMENT");
  await error(() => f.prepare({source: {...f.source, admitted: true}}), "INVALID_ARGUMENT");
});
test("course must belong to authenticated author; curator cannot substitute for course ownership", async () => {
  const f = fixture(); await error(() => f.prepare({}, "curator"), "SOURCE_NOT_ELIGIBLE"); assert.equal(f.db.commits.length, 0);
});
for (const [name, path, patch] of [
  ["retired course", "quests/course", {visibleOn: []}],
  ["home-only course", "quests/course", {visibleOn: ["home"]}],
  ["regular archive", "catalogs/catalog", {questType: "REGULAR"}],
  ["archived catalog", "catalogs/catalog", {archived: true}],
]) test(`PREPARE rejects ${name}`, async () => {
  const f = fixture(); change(f, path, patch); await error(() => f.prepare(), "SOURCE_NOT_ELIGIBLE"); assert.equal(f.db.commits.length, 0);
});
for (const path of ["sections/section", "themes/theme", "lessons/lesson"]) test(`archived descendant excluded: ${path}`, async () => {
  const f = fixture(); change(f, path, {archived: true}); await error(() => f.prepare(), "INSUFFICIENT_QUESTIONS");
});
test("unapproved independent review and mismatched submission exclude lessons", async () => {
  const f = fixture({reviewApproved: false}); await error(() => f.prepare(), "INSUFFICIENT_QUESTIONS");
  f.state.reviewApproved = true; change(f, "lessons/lesson", {reviewSubmissionId: "other-submission"});
  await error(() => f.prepare(), "INSUFFICIENT_QUESTIONS");
});
test("unapproved extra lesson is excluded without replacing the valid course bank", async () => {
  const f = fixture(); change(f, "lessons/other", {themeId: "theme", version: 1, reviewSubmissionId: "unapproved"});
  change(f, "questions/not-approved", {lessonId: "other", version: 1, payload: JSON.stringify(content(1))});
  assert.equal((await f.prepare()).counts.total, 80);
});
test("foreign hierarchy cannot be attested into the selected course", async () => {
  const f = fixture(); const real = f.loaders.loadLessonContext;
  const service = createTournamentSourceBank({db: f.db, ...f.loaders, loadLessonContext: async (...args) => ({...await real(...args), questId: "foreign"})});
  await error(() => service.prepare({uid, payload: {requestId, source: f.source}}), "INVALID_SOURCE");
});
test("translations collapse by existing canonical ID; selected IDs remain real documents", async () => {
  const f = fixture(); for (let i = 0; i < 80; i++) {
    const old = `questions/q${String(i).padStart(3, "0")}`; const row = f.db.store.get(old);
    f.db.store.delete(old); f.db.store.set(`${old}__en`, {...row, language: "en"}); f.db.store.set(`${old}__ru`, {...row, language: "ru"});
  }
  const result = await f.prepare(); assert.equal(result.counts.total, 80);
  assert.ok(bank(f, result).questions.every((q) => q.questionId.endsWith("__en")));
});
test("translations cannot inflate shortage or disagree on difficulty", async () => {
  const f = fixture(); f.db.store.delete("questions/q000");
  f.db.store.set("questions/q001__ru", f.db.store.get("questions/q001"));
  await error(() => f.prepare(), "INSUFFICIENT_QUESTIONS");
  change(f, "questions/q001__ru", {payload: JSON.stringify(content(79))});
  await error(() => f.prepare(), "INVALID_SOURCE");
});
test("surveys and archived questions do not count toward 60 EASY/20 HARD", async () => {
  const f = fixture(); change(f, "questions/survey", {lessonId: "lesson", version: 1, payload: JSON.stringify(content(1, "Survey"))});
  change(f, "questions/archived", {lessonId: "lesson", version: 1, archived: true, payload: "invalid but archived"});
  assert.equal((await f.prepare()).counts.total, 80);
  const g = fixture(); change(g, "questions/q000", {payload: JSON.stringify(content(1, "Survey"))});
  await error(() => g.prepare(), "INSUFFICIENT_QUESTIONS");
});
test("already-redacted source restores current key then produces a single new matching pair", async () => {
  const f = fixture({types: ["Ordering"]}); const keys = [];
  for (const [path, row] of f.db.store) if (path.startsWith("questions/")) {
    const questionId = path.split("/")[1]; const original = {...JSON.parse(row.payload), id: questionId};
    const pair = redact(JSON.stringify(original), original.difficulty, {questionId, random: () => 0.9});
    keys.push({questionId, key: pair.key}); change(f, path, {payload: pair.publicPayload});
  }
  f.db.store.set("question_keys/lesson", {version: 1, lessonId: "lesson", publicHalfRedacted: true, keys});
  const result = await f.prepare(); assert.ok(bank(f, result).questions.every((q) => restoreContent(q.publicPayload, q.answerKey)));
  change(f, "question_keys/lesson", {publicHalfRedacted: false});
  await error(() => f.load(result), "INVALID_SOURCE");
});
test("missing key, wrong key owner and duplicated key are not fabricated into playable questions", async () => {
  for (const mode of ["missing", "wrong-owner", "duplicate"]) {
    const f = fixture(); const original = {...content(0), id: "q000"};
    const pair = redact(JSON.stringify(original), "EASY", {questionId: "q000"}); change(f, "questions/q000", {payload: pair.publicPayload});
    if (mode !== "missing") f.db.store.set("question_keys/lesson", {version: 1, lessonId: "lesson", publicHalfRedacted: true,
      keys: mode === "duplicate" ? [{questionId: "q000", key: pair.key}, {questionId: "q000", key: pair.key}] : [{questionId: "q000", key: {...pair.key, questionId: "foreign"}}]});
    await error(() => f.prepare(), "INVALID_SOURCE"); assert.equal(f.db.commits.length, 0);
  }
});
test("Arena requires current admitted version plus own author or trusted profile curator", async () => {
  const f = fixture({arena: true}); await f.prepare(); assert.equal(f.state.curatorLoads, 0);
  const g = fixture({arena: true}); const response = await g.prepare({}, "curator");
  assert.equal(bank(g, response).authorizedUid, "curator"); assert.equal(g.state.curatorLoads, 1);
  await error(() => g.prepare({requestId: "different_request_0001"}, "other"), "PERMISSION_DENIED");
  change(g, "profiles/curator", {curator: false}); await error(() => g.load(response, "CREATE", "curator"), "PERMISSION_DENIED");
  assert.ok(await g.load(response, "RECOVERY", "curator"));
});
for (const patch of [{shelf: "retired"}, {shelf: "tournament"}, {contentVersion: 3}, {authorUid: "other"}]) test(`Arena refuses stale placement ${JSON.stringify(patch)}`, async () => {
  const f = fixture({arena: true}); change(f, "lesson_placements/lesson", patch); await error(() => f.prepare(), "SOURCE_NOT_ELIGIBLE");
});
test("CREATE revalidates complete content even when publisher forgot to increment version", async () => {
  const f = fixture(); const response = await f.prepare(); const row = f.db.store.get("questions/q000");
  change(f, "questions/q000", {payload: JSON.stringify({...JSON.parse(row.payload), text: "Edited"})});
  await error(() => f.load(response), "STALE_PREPARATION");
});
for (const [path, patch] of [
  ["questions/q000", {version: 5}], ["themes/theme", {version: 8}], ["quests/course", {version: 8}],
  ["lessons/lesson", {reviewApprovedAtMs: T}],
]) test(`CREATE rejects changed bound proof ${path}`, async () => {
  const f = fixture(); const response = await f.prepare(); change(f, path, patch); await error(() => f.load(response), "STALE_PREPARATION");
});
test("CREATE rejects newly added question rather than silently ignoring it", async () => {
  const f = fixture(); const response = await f.prepare();
  change(f, "questions/added", {lessonId: "lesson", version: 1, payload: JSON.stringify(content(0))});
  await error(() => f.load(response), "STALE_PREPARATION");
});
test("CREATE rejects retired source but CLOSE/RECOVERY retain original bank despite edits/deletion/role loss", async () => {
  const f = fixture(); const response = await f.prepare(); const original = await f.load(response);
  change(f, "quests/course", {visibleOn: []}); f.state.reviewApproved = false; f.db.store.delete("questions/q000");
  await error(() => f.load(response), "SOURCE_NOT_ELIGIBLE"); const loads = f.state.contextLoads;
  assert.deepEqual(await f.load(response, "CLOSE_QUALIFICATION"), original); assert.deepEqual(await f.load(response, "RECOVERY"), original);
  assert.equal(f.state.contextLoads, loads);
});
test("unknown load purpose and private snapshot integrity failure are fail-closed", async () => {
  const f = fixture(); const response = await f.prepare();
  await error(() => f.load(response, "START"), "INVALID_ARGUMENT");
  change(f, `${SOURCE_BANKS}/${response.snapshotId}`, {bankHash: "corrupted"});
  await error(() => f.load(response, "RECOVERY"), "INVALID_SOURCE");
});
for (const limits of [{hierarchyRows: 2}, {questionRows: 79}, {selectedQuestions: 79}, {readDocuments: 10}, {queryDocuments: 79}, {snapshotBytes: 1000}]) {
  test(`explicit complete-source bound rejects atomically ${JSON.stringify(limits)}`, async () => {
    const f = fixture({limits}); await error(() => f.prepare(), "SOURCE_TOO_LARGE"); assert.equal(f.db.commits.length, 0);
  });
}
test("unbounded injected review query receives no silently truncated result", async () => {
  const f = fixture({limits: {queryDocuments: 80}});
  for (let i = 0; i < 81; i++) f.db.store.set(`reviews/r${i}`, {lessonId: "lesson"});
  const service = createTournamentSourceBank({db: f.db, ...f.loaders, limits: {queryDocuments: 80},
    loadLessonContext: async (tx) => { await tx.get(f.db.collection("reviews").where("lessonId", "==", "lesson")); throw Error("must never receive partial reviews"); }});
  await error(() => service.prepare({uid, payload: {requestId, source: f.source}}), "SOURCE_TOO_LARGE");
});
test("commit failure writes neither snapshot nor receipt; retry safely prepares", async () => {
  const f = fixture(); f.db.failNextCommit(); await assert.rejects(() => f.prepare(), /injected atomic/);
  assert.equal([...f.db.store.keys()].filter((p) => p.startsWith(SOURCE_BANKS) || p.startsWith(PREPARATIONS)).length, 0);
  assert.equal((await f.prepare()).counts.total, 80);
});

test("Firestore reused Transaction object retries fresh eligibility reads, not stale memoized context", async () => {
  const f = fixture(); const run = f.db.runTransaction.bind(f.db); let active; let attempt = 0;
  const sharedTx = {get: (...args) => active.get(...args), create: (...args) => active.create(...args), set: (...args) => active.set(...args)};
  f.db.runTransaction = async (body) => {
    try { await run(async (tx) => { active = tx; await body(sharedTx); throw Error("simulated-conflict"); }); }
    catch (e) { assert.equal(e.message, "simulated-conflict"); }
    attempt++; change(f, "quests/course", {visibleOn: []});
    return run(async (tx) => { active = tx; return body(sharedTx); });
  };
  await error(() => f.prepare(), "SOURCE_NOT_ELIGIBLE"); assert.equal(attempt, 1);
  assert.equal([...f.db.store.keys()].filter((p) => p.startsWith(SOURCE_BANKS)).length, 0);
});

test("optimistic retry retains the same public/private permutation without caching the transaction read set", async () => {
  const f = fixture({types: ["Ordering", "FillBlank"]}); const run = f.db.runTransaction.bind(f.db); let active; const candidates = [];
  const sharedTx = {get: (...args) => active.get(...args), create: (ref, row) => {
    if (ref.path.startsWith(`${SOURCE_BANKS}/`)) candidates.push(structuredClone(row));
    active.create(ref, row);
  }};
  f.db.runTransaction = async (body) => {
    try { await run(async (tx) => { active = tx; await body(sharedTx); throw Error("simulated-conflict"); }); }
    catch (e) { assert.equal(e.message, "simulated-conflict"); }
    return run(async (tx) => { active = tx; return body(sharedTx); });
  };
  await f.prepare(); assert.equal(candidates.length, 2); assert.deepEqual(candidates[0], candidates[1]); assert.equal(f.state.contextLoads, 2);
});

test("Arena ancestor version change invalidates CREATE without altering the recovery bank", async () => {
  const f = fixture({arena: true}); const response = await f.prepare(); const original = bank(f, response);
  change(f, "quests/course", {version: 100}); await error(() => f.load(response), "STALE_PREPARATION");
  assert.deepEqual(await f.load(response, "RECOVERY"), original);
});

test("actual lifecycle CREATE revalidation failure cannot reserve organizer boxes or write event/ledger", async () => {
  const f = fixture(); const response = await f.prepare(); change(f, "questions/q000", {version: 99});
  const prior = structuredClone([...f.db.store]);
  await error(() => applyTournamentOperation({db: f.db, uid, now: T, serverSeed: "server-only-seed-0000000000000000000",
    dependencies: {loadBank: f.service.loadBank}, payload: {kind: "CREATE", ownerUid: uid, operationId: "create_operation_0001", eventId: "event_000000000000001",
      source: {...f.source, snapshotId: response.snapshotId}, startsAtMs: T, fundedBoxes: 12, prizes: {listVersion: 1,
      rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 3}, {place: 3, amountBoxes: 1}, {place: 3, amountBoxes: 1}]}}}), "STALE_PREPARATION");
  assert.deepEqual([...f.db.store], prior);
});

test("actual lifecycle passes CREATE/CLOSE purpose and uses immutable original bank after source retirement", async () => {
  const f = fixture(); const response = await f.prepare(); const purposes = [];
  const dependencies = {loadBank: async (args) => { purposes.push(args.purpose); return f.service.loadBank(args); },
    loadQualificationRanking: async ({eventId, forms, endsAtMs}) => buildQualificationRankingSnapshot({eventId, forms, endsAtMs,
      snapshotId: "freeze_complete_00001", completeThroughMs: endsAtMs, pendingSessionCount: 0,
      verifiedRuns: ["alice", "bob"].flatMap((player, i) => forms.map((form, j) => ({eventId, uid: player, sessionId: `${player}_${j}`,
        status: "SETTLED", formId: form.formId, formFingerprint: form.fingerprint, verificationVersion: "server-v1",
        percentScore: 90 - i, elapsedMs: 1000 + i, startedAtMs: T, completedAtMs: T + 1000 + i})))}),};
  const common = {db: f.db, uid, serverSeed: "server-only-seed-0000000000000000000", dependencies};
  const eventId = "event_000000000000001";
  await applyTournamentOperation({...common, now: T, payload: {kind: "CREATE", ownerUid: uid, operationId: "create_operation_0001", eventId,
    source: {...f.source, snapshotId: response.snapshotId}, startsAtMs: T, fundedBoxes: 12, prizes: {listVersion: 1,
    rows: [{place: 1, amountBoxes: 5}, {place: 2, amountBoxes: 3}, {place: 3, amountBoxes: 1}, {place: 3, amountBoxes: 1}]}}});
  assert.equal(f.db.store.get("users/author").boxCount, 88);
  change(f, "quests/course", {visibleOn: []}); f.state.reviewApproved = false;
  await applyTournamentOperation({...common, now: T + 7 * 86400000, payload: {kind: "CLOSE_QUALIFICATION", ownerUid: uid, operationId: "close_operation_00001", eventId}});
  assert.deepEqual(purposes, ["CREATE", "CLOSE_QUALIFICATION"]);
  assert.equal(f.db.store.get(`course_tournament_events/${eventId}`).status, "BRACKET");
});

(async () => {
  for (const {name, fn} of tests) { try { await fn(); } catch (e) { console.error(`FAIL ${name}`); throw e; } }
  console.log(`tournament-source-bank: ${tests.length} behavioral cases passed`);
})().catch((error) => { console.error(error); process.exitCode = 1; });
