"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");

const {
  OWNER_VISIBLE_STATUSES,
  PROPOSALS_VIEW_VERSION,
  buildProposalsPage,
  parseQuestProposalsRequest,
  readCurrentRevision,
  rowForProposal,
  settleProposalFeeCycle,
} = require("./quest-proposals-view");

function proposal(overrides = {}) {
  return {
    schemaVersion: 1,
    proposalId: overrides.proposalId || "proposal-1",
    kind: overrides.kind || "REPORT",
    proposerUid: "proposer",
    targetOwnerUid: "author",
    status: overrides.status || "CHECK_PASSED",
    target: overrides.target || {lessonId: "lesson-1"},
    baseRevision: overrides.baseRevision !== undefined ? overrides.baseRevision : 3,
    payload: overrides.payload !== undefined ? overrides.payload : {text: "текст отчёта"},
    fee: overrides.fee !== undefined ? overrides.fee : {nolics: 0, changedQuestions: 0},
    decision: overrides.decision !== undefined ? overrides.decision : null,
    createdAtMs: overrides.createdAtMs !== undefined ? overrides.createdAtMs : 100,
    checkedAtMs: 0,
    decidedAtMs: 0,
  };
}

function doc(id, data) {
  return {id, data};
}

test("request parses box, bounds the page and refuses the unknown box", () => {
  assert.deepEqual(parseQuestProposalsRequest({box: "OUTGOING", limit: "30", cursor: "p-2"}),
    {box: "OUTGOING", limit: 30, cursor: "p-2"});
  assert.deepEqual(parseQuestProposalsRequest({}), {box: "INCOMING", limit: 20, cursor: null});
  assert.equal(parseQuestProposalsRequest({limit: 999}).limit, 50);
  assert.throws(() => parseQuestProposalsRequest({box: "MINE"}), /Unknown proposals box/);
  assert.throws(() => parseQuestProposalsRequest({limit: 0}), /limit must be positive/);
  assert.throws(() => parseQuestProposalsRequest({cursor: "a/b"}), /cursor must be a proposal id/);
});

test("owner shelf hides checks that never reached the author; proposer sees everything", () => {
  for (const status of ["QUEUED", "CHECK_FAILED", "CHECK_DELAYED"]) {
    assert.equal(rowForProposal("p", proposal({status}), "owner", 3), null,
      `owner must not see ${status}`);
  }
  for (const status of ["QUEUED", "CHECK_FAILED", "CHECK_PASSED", "APPROVED", "REJECTED"]) {
    const row = rowForProposal("p", proposal({status}), "proposer", 3);
    assert.ok(row && row.status === status, `proposer sees ${status}`);
  }
  assert.ok(OWNER_VISIBLE_STATUSES.includes("CHECK_PASSED"));
});

test("row carries the report body and the decision for both sides", () => {
  const raw = proposal({
    decision: {decision: "REJECT", comment: "не по делу", decidedAtMs: 7},
  });
  for (const role of ["owner", "proposer"]) {
    const row = rowForProposal("p", raw, role, 3);
    assert.equal(row.proposalText, "текст отчёта");
    assert.deepEqual(row.decision, {decision: "REJECT", comment: "не по делу", decidedAtMs: 7});
  }
});

test("stale is computed against the current revision; unknown revision does not invent stale", () => {
  assert.equal(rowForProposal("p", proposal({baseRevision: 3}), "owner", 3).stale, false);
  assert.equal(rowForProposal("p", proposal({baseRevision: 3}), "owner", 4).stale, true);
  assert.equal(rowForProposal("p", proposal({baseRevision: 3}), "owner", null).stale, false);
});

test("copy edit rows carry question previews with the edited flag", () => {
  const raw = proposal({
    kind: "COPY_EDIT",
    payload: {
      lessonTitle: "Урок",
      editedQuestionIds: ["q2"],
      questions: [
        {id: "q1", text: "нетронутый"},
        {id: "q2", text: "исправленный"},
      ],
    },
  });
  const row = rowForProposal("p", raw, "owner", 3);
  assert.equal(row.lessonTitle, "Урок");
  assert.deepEqual(row.questionPreviews, [
    {id: "q1", text: "нетронутый", edited: false},
    {id: "q2", text: "исправленный", edited: true},
  ]);
});

test("unreadable record becomes a marked row, not silence (AD-10)", () => {
  const row = rowForProposal("broken", {schemaVersion: 2}, "owner", 3);
  assert.deepEqual(row, {unreadable: true, proposalId: "broken"});
});

test("page sorts by recency, bounds by limit and names the next cursor", () => {
  const docs = [
    doc("old", proposal({proposalId: "old", createdAtMs: 50})),
    doc("new", proposal({proposalId: "new", createdAtMs: 200})),
    doc("mid", proposal({proposalId: "mid", createdAtMs: 100})),
  ];
  const page = buildProposalsPage({box: "INCOMING", viewerRole: "owner", docs, revisions: {}, limit: 2});
  assert.equal(page.view, PROPOSALS_VIEW_VERSION);
  assert.deepEqual(page.rows.map((row) => row.proposalId), ["new", "mid"]);
  assert.equal(page.hasMore, true);
  assert.equal(page.nextCursor, "mid");
  const last = buildProposalsPage({box: "INCOMING", viewerRole: "owner", docs, revisions: {}, limit: 3});
  assert.equal(last.hasMore, false);
  assert.equal(last.nextCursor, null);
});

test("current revision reads the lesson or the theme document", async () => {
  const docs = {
    "lessons/lesson-1": {exists: true, data: () => ({version: 5})},
    "themes/theme-1": {exists: true, data: () => ({version: 2})},
  };
  const db = {doc: (path) => ({get: async () => docs[path]})};
  assert.equal(await readCurrentRevision(db, {target: {lessonId: "lesson-1"}}), 5);
  assert.equal(await readCurrentRevision(db, {target: {themeId: "theme-1"}}), 2);
  assert.equal(await readCurrentRevision(db, {target: {}}), null);
});

test("fee settlement burns the whole fee once; repeats replay; non-terminal refuses", async () => {
  const created = [];
  const db = {
    doc: (path) => ({
      create: async (manifest) => {
        if (created.includes(path)) {
          const error = new Error("exists");
          error.code = 6;
          throw error;
        }
        created.push(path);
        assert.ok(path.startsWith("review_proposal_fee_manifests/"));
        return manifest;
      },
    }),
  };
  const proposal100 = proposal({status: "REJECTED", fee: {nolics: 300, changedQuestions: 3}});
  const first = await settleProposalFeeCycle(db, "proposal-1", proposal100, 42);
  assert.deepEqual(first, {settled: true, replayed: false, burnTenths: 3000});
  const replay = await settleProposalFeeCycle(db, "proposal-1", proposal100, 43);
  assert.deepEqual(replay, {settled: true, replayed: true});
  const pending = await settleProposalFeeCycle(db, "proposal-1", proposal({status: "QUEUED"}), 44);
  assert.deepEqual(pending, {settled: false, reason: "not-terminal"});
  const free = await settleProposalFeeCycle(db, "proposal-2", proposal({status: "CHECK_FAILED"}), 45);
  assert.deepEqual(free, {settled: true, replayed: false, burnTenths: 0});
});

test("settlement refuses an unfunded manifest path collision loudly", async () => {
  // ALREADY_EXISTS понимается только как replay; прочий сбой хранилища летит дальше.
  const db = {
    doc: () => ({
      create: async () => {
        const error = new Error("storage down");
        error.code = 14;
        throw error;
      },
    }),
  };
  await assert.rejects(
    () => settleProposalFeeCycle(db, "proposal-1", proposal({status: "APPROVED"}), 1),
    /storage down/,
  );
});
