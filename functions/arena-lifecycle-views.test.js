"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  ArenaLifecycleViewError, VIEW_VERSION, REVIEW_SUBMISSION_VIEW_VERSION, createArenaLifecycleViews,
} = require("./arena-lifecycle-views");

function compare(left, right) { return left < right ? -1 : left > right ? 1 : 0; }
function parent(path) { return path.slice(0, path.lastIndexOf("/")); }
function id(path) { return path.slice(path.lastIndexOf("/") + 1); }
function snapshot(path, documents) {
  const data = documents.get(path);
  return {id: id(path), ref: {path}, exists: data !== undefined,
    data: () => data === undefined ? undefined : structuredClone(data)};
}

function fakeFirestore(seed, contexts, curatorUids = []) {
  const documents = new Map(Object.entries(seed));
  const reads = []; const writes = []; const queries = [];
  const valueOf = (path, field) => field === "__name__" ? id(path) : documents.get(path)[field];
  function children(path) {
    const prefix = path + "/";
    return [...documents.keys()].filter(candidate => candidate.startsWith(prefix) && !candidate.slice(prefix.length).includes("/"));
  }
  function query(path, state = {filters: [], order: [], after: null, maximum: Infinity}) {
    const next = patch => query(path, {...state, ...patch});
    return {
      path,
      querySnapshot() {
        let rows = children(path).filter(row => state.filters.every(filter => valueOf(row, filter.field) === filter.value));
        rows = rows.filter(row => state.order.every(item => valueOf(row, item.field) !== undefined));
        rows.sort((left, right) => {
          for (const item of state.order) {
            const order = compare(valueOf(left, item.field), valueOf(right, item.field));
            if (order !== 0) return item.direction === "desc" ? -order : order;
          }
          return 0;
        });
        if (state.after !== null) {
          assert.equal(state.after.length, state.order.length);
          rows = rows.filter(row => {
            for (let index = 0; index < state.order.length; index += 1) {
              const item = state.order[index];
              const order = compare(valueOf(row, item.field), state.after[index]);
              if (order === 0) continue;
              return item.direction === "desc" ? order < 0 : order > 0;
            }
            return false;
          });
        }
        return {docs: rows.slice(0, state.maximum).map(row => snapshot(row, documents))};
      },
      where(field, operator, value) {
        assert.equal(operator, "==");
        return next({filters: [...state.filters, {field, value}]});
      },
      orderBy(field, direction = "asc") {
        assert.ok(["asc", "desc"].includes(direction));
        return next({order: [...state.order, {field, direction}]});
      },
      startAfter(...values) { return next({after: values}); },
      limit(maximum) { return next({maximum}); },
    };
  }
  const db = {
    doc(path) { return {path, querySnapshot: null}; },
    collection(path) { return query(path); },
    async runTransaction(body) {
      const tx = {
        get: async ref => {
          if (typeof ref.querySnapshot === "function") {
            queries.push({path: ref.path, ...ref._state});
            reads.push({kind: "query", path: ref.path});
            return ref.querySnapshot();
          }
          reads.push({kind: "document", path: ref.path});
          return snapshot(ref.path, documents);
        },
      };
      return body(tx);
    },
  };
  const loadLessonContext = async (_, lessonId) => {
    reads.push({kind: "context", lessonId});
    return Object.hasOwn(contexts, lessonId) ? structuredClone(contexts[lessonId]) : null;
  };
  const isCurator = async (_, uid) => {
    reads.push({kind: "curator", uid});
    return curatorUids.includes(uid);
  };
  return {db, reads, writes, queries, documents, loadLessonContext, isCurator};
}

function context(lessonId, authorUid, version = 2) {
  return {lessonId, authorUid, questType: "REGULAR", contentVersion: version, published: true, reviewApproved: true};
}
function placement(lessonId, authorUid, patch = {}) {
  return {lessonId, authorUid, questType: "REGULAR", shelf: "arena", previousShelf: "review",
    contentVersion: 1, placementVersion: 2, updatedAtMs: 200, ...patch};
}
function partialRejection(lessonId, patch = {}) {
  return {lessonId, shelf: "review", lastRejection: {
    code: "REVIEW_APPROVAL_REQUIRED", reason: "PRIVATE_REJECTION_REASON",
    actorUid: "PRIVATE_ACTOR", atMs: 150,
  }, futurePrivate: "PRIVATE_PARTIAL_PLACEMENT", ...patch};
}
function rating(lessonId, patch = {}) {
  return {lessonId, authorUid: "PRIVATE_RATING_AUTHOR", ratingSum: 5, ratingCount: 2,
    averageRating: 2.5, ratingVersion: 4, ratingUpdatedAtMs: 300,
    futurePrivate: "PRIVATE_RATING", ...patch};
}
const EMPTY_RATING = Object.freeze({sum: 0, count: 0, average: null, version: 0, updatedAtMs: null});
function history(decidedAtMs, patch = {}) {
  return {actorUid: "PRIVATE_ACTOR", mutationId: "PRIVATE_MUTATION", reason: "PRIVATE_REASON",
    fromShelf: "review", targetShelf: "arena", status: "APPLIED", code: null, decidedAtMs,
    futurePrivate: "PRIVATE_HISTORY", ...patch};
}
function ledger(uid, atMs, delta, patch = {}) {
  return {uid, currency: "nolics", delta, reason: "arena_author_royalty", atMs,
    sourceId: "PRIVATE_TICKET", details: {payerUid: "PRIVATE_PAYER", attemptId: "PRIVATE_ATTEMPT"},
    futurePrivate: "PRIVATE_LEDGER", ...patch};
}
function reviewSubmission(ownerUid, draftId, status, processedAtMs, patch = {}) {
  return {ownerUid, draftId, status, processedAtMs, processed: true,
    questions: [{id: "PRIVATE_QUESTION", payload: "PRIVATE_ANSWER"}],
    draft: {title: "PRIVATE_DRAFT"}, review: {futurePrivate: "PRIVATE_REVIEW"},
    lastError: "PRIVATE_ERROR", ...patch};
}
function world() {
  const contexts = {
    "lesson-a": context("lesson-a", "author", 2),
    "lesson-b": context("lesson-b", "author", 1),
    "lesson-foreign": context("lesson-foreign", "other", 1),
    "lesson-review": context("lesson-review", "author", 3),
    "lesson-partial-rejection": context("lesson-partial-rejection", "author", 3),
  };
  const seed = {
    "lesson_placements/lesson-a": placement("lesson-a", "author", {futurePrivate: "PRIVATE_PLACEMENT"}),
    "lesson_placements/lesson-b": placement("lesson-b", "author", {shelf: "tournament", previousShelf: "arena", contentVersion: 1}),
    "lesson_placements/lesson-foreign": placement("lesson-foreign", "other"),
    "lesson_placements/lesson-partial-rejection": partialRejection("lesson-partial-rejection"),
    "lesson_ratings/lesson-a": rating("lesson-a"),
    "lesson_placements/lesson-a/history/history-a": history(100),
    "lesson_placements/lesson-a/history/history-b": history(100, {targetShelf: "rejected"}),
    "lesson_placements/lesson-a/history/history-c": history(200, {status: "REJECTED",
      code: "REVIEW_APPROVAL_REQUIRED", targetShelf: "arena"}),
    "arena_royalty_accounts/author": {totalAuthorCreditNolics: 13, updatedAtMs: 500,
      authorCarryFifths: 4, sinkCarryFifths: 1, reservedNolics: 1, totalPaymentsNolics: 17,
      totalSinkNolics: 3, futurePrivate: "PRIVATE_ACCOUNT"},
    "audit/currency_ledger/entries/royalty-a": ledger("author", 100, 1),
    "audit/currency_ledger/entries/royalty-b": ledger("author", 200, 2),
    "audit/currency_ledger/entries/royalty-c": ledger("author", 200, 3),
    "audit/currency_ledger/entries/not-royalty": ledger("author", 400, 99, {reason: "attempt_reward"}),
    "audit/currency_ledger/entries/foreign-royalty": ledger("other", 500, 77),
    "quest_review_requests/submission-published": reviewSubmission("author", "draft-published", "PUBLISHED", 100),
    "quest_review_requests/submission-rejected": reviewSubmission("author", "draft-rejected", "REJECTED", 110,
      {rejectionReason: "Correct the unsupported question format"}),
    "quest_review_requests/submission-foreign": reviewSubmission("other", "draft-foreign", "PUBLISHED", 120),
    "quest_review_requests/legacy-old": reviewSubmission("author", "draft-legacy", "REJECTED", 130,
      {rejectionReason: "Old author-visible reason"}),
    "quest_review_requests/legacy-current": reviewSubmission("author", "draft-legacy", "UNDER_REVIEW", 140),
  };
  return {seed, contexts};
}
function fixture() {
  const data = world(); const fake = fakeFirestore(data.seed, data.contexts, ["curator"]);
  return {...fake, views: createArenaLifecycleViews({
    db: fake.db, loadLessonContext: fake.loadLessonContext, isCurator: fake.isCurator,
    readCursorKey: () => Buffer.alloc(32, 7),
  })};
}
function rejects(action, code) {
  return assert.rejects(action, error => error instanceof ArenaLifecycleViewError && error.code === code);
}
function noPrivate(value) {
  const text = JSON.stringify(value);
  for (const token of ["PRIVATE_", "\"actorUid\"", "\"authorUid\"", "\"mutationId\"", "\"reason\":", "\"sourceId\"", "\"details\"",
    "authorCarryFifths", "sinkCarryFifths", "reservedNolics", "totalPaymentsNolics", "totalSinkNolics",
    "questions", "processedAtMs", "lastError", "payload"]) {
    assert.equal(text.includes(token), false, token);
  }
}
function hiddenInCursor(cursor, ...rawAnchors) {
  const bytes = Buffer.from(cursor, "base64url");
  for (const anchor of rawAnchors) {
    assert.equal(bytes.includes(Buffer.from(anchor, "utf8")), false, anchor);
  }
}

test("payloads are strict and invalid authority data is refused before every domain read", async () => {
  const f = fixture();
  for (const input of [
    null, {uid: "author"}, {uid: "author", payload: null},
    {uid: "author", payload: {pageSize: 0}}, {uid: "author", payload: {pageSize: 21}},
    {uid: "author", payload: {pageSize: 1, ownerUid: "other"}},
  ]) await rejects(f.views.listOwned(input), "INVALID_ARGUMENT");
  await rejects(f.views.listOwned({}), "UNAUTHENTICATED");
  await rejects(f.views.get({uid: "author", payload: {lessonId: "lesson-a", uid: "other"}}), "INVALID_ARGUMENT");
  await rejects(f.views.listOwned({uid: "", payload: {}}), "UNAUTHENTICATED");
  assert.deepEqual(f.reads, []); assert.deepEqual(f.writes, []);
});

test("owner list uses an exclusive bounded document cursor, validates current ownership, and returns an allowlist DTO", async () => {
  const f = fixture();
  const first = await f.views.listOwned({uid: "author", payload: {pageSize: 1}});
  assert.deepEqual(first, {viewVersion: VIEW_VERSION, entries: [{
    lessonId: "lesson-a", currentContentVersion: 2, published: true, reviewApproved: true, state: "PLACED",
    rating: {sum: 5, count: 2, average: 2.5, version: 4, updatedAtMs: 300},
    placement: {shelf: "arena", previousShelf: "review", contentVersion: 1, currentVersion: false,
      placementVersion: 2, updatedAtMs: 200},
  }], nextLessonId: "lesson-a"});
  noPrivate(first);
  const second = await f.views.listOwned({uid: "author", payload: {pageSize: 1, afterLessonId: first.nextLessonId}});
  assert.deepEqual(second.entries.map(row => row.lessonId), ["lesson-b"]);
  // The cursor is exclusive: the second page starts strictly after the returned anchor.
  const complete = await f.views.listOwned({uid: "author", payload: {pageSize: 20}});
  assert.deepEqual(complete.entries.map(row => row.lessonId), ["lesson-a", "lesson-b"]);
  assert.deepEqual(f.writes, []);
});

test("current root ownership parity fails closed instead of trusting a stale placement author", async () => {
  const f = fixture();
  f.contexts = undefined;
  f.documents.set("lesson_placements/lesson-a", placement("lesson-a", "author"));
  const rawContext = f.loadLessonContext;
  f.views = createArenaLifecycleViews({db: f.db, isCurator: f.isCurator,
    loadLessonContext: async (tx, lessonId) => lessonId === "lesson-a" ? context("lesson-a", "other") : rawContext(tx, lessonId),
    readCursorKey: () => Buffer.alloc(32, 7)});
  await rejects(f.views.listOwned({uid: "author", payload: {}}), "UNAVAILABLE");
  assert.deepEqual(f.writes, []);
});

test("a concrete lifecycle is owner-or-curator scoped, represents an unplaced or first-rejected lesson as REVIEW, and hides existence from outsiders", async () => {
  const f = fixture();
  const review = await f.views.get({uid: "author", payload: {lessonId: "lesson-review"}});
  assert.deepEqual(review, {viewVersion: VIEW_VERSION, lifecycle: {
    lessonId: "lesson-review", currentContentVersion: 3, published: true, reviewApproved: true,
    rating: EMPTY_RATING,
    state: "REVIEW", placement: null,
  }});
  const partial = await f.views.get({uid: "author", payload: {lessonId: "lesson-partial-rejection"}});
  assert.deepEqual(partial, {viewVersion: VIEW_VERSION, lifecycle: {
    lessonId: "lesson-partial-rejection", currentContentVersion: 3, published: true, reviewApproved: true,
    rating: EMPTY_RATING,
    state: "REVIEW", placement: null,
  }});
  const curator = await f.views.get({uid: "curator", payload: {lessonId: "lesson-foreign"}});
  assert.equal(curator.lifecycle.lessonId, "lesson-foreign");
  await rejects(f.views.get({uid: "author", payload: {lessonId: "lesson-foreign"}}), "NOT_FOUND");
  await rejects(f.views.get({uid: "outsider", payload: {lessonId: "lesson-foreign"}}), "NOT_FOUND");
  noPrivate(curator); assert.deepEqual(f.writes, []);
});

test("curator list checks only the server role and a plain author cannot spoof that role", async () => {
  const f = fixture();
  await rejects(f.views.listCurator({uid: "author", payload: {pageSize: 1}}), "PERMISSION_DENIED");
  const result = await f.views.listCurator({uid: "curator", payload: {pageSize: 20}});
  assert.deepEqual(result.entries.map(row => row.lessonId), ["lesson-a", "lesson-b", "lesson-foreign", "lesson-partial-rejection"]);
  assert.deepEqual(result.entries.at(-1).placement, null);
  noPrivate(result); assert.deepEqual(f.writes, []);
});

test("history uses an encrypted account-and-lesson-bound cursor, preserves duplicate timestamps, and never projects actor or mutation data", async () => {
  const f = fixture();
  const first = await f.views.listHistory({uid: "author", payload: {lessonId: "lesson-a", pageSize: 1}});
  assert.deepEqual(first.entries, [{fromShelf: "review", targetShelf: "arena", status: "APPLIED",
    reasonCode: null, decidedAtMs: 100}]);
  assert.equal(typeof first.nextCursor, "string"); noPrivate(first);
  hiddenInCursor(first.nextCursor, "history-a", "PRIVATE_ACTOR", "PRIVATE_MUTATION");
  const second = await f.views.listHistory({uid: "author", payload: {lessonId: "lesson-a", pageSize: 1, cursor: first.nextCursor}});
  assert.deepEqual(second.entries, [{fromShelf: "review", targetShelf: "rejected", status: "APPLIED",
    reasonCode: "REVIEW_REJECTED", decidedAtMs: 100}]);
  await rejects(f.views.listHistory({uid: "other", payload: {lessonId: "lesson-a", cursor: first.nextCursor}}), "INVALID_ARGUMENT");
  const tampered = first.nextCursor.slice(0, -1) + (first.nextCursor.endsWith("A") ? "B" : "A");
  await rejects(f.views.listHistory({uid: "author", payload: {lessonId: "lesson-a", cursor: tampered}}), "INVALID_ARGUMENT");
  await rejects(f.views.listHistory({uid: "outsider", payload: {lessonId: "lesson-a"}}), "NOT_FOUND");
  assert.deepEqual(f.writes, []);
});

test("royalty reader returns only actual author credits, uses a descending bounded cursor, and keeps payer/source/carry data private", async () => {
  const f = fixture();
  const first = await f.views.listMyRoyalty({uid: "author", payload: {pageSize: 2}});
  assert.deepEqual(first.summary, {totalCreditedNolics: 13, updatedAtMs: 500});
  assert.deepEqual(first.entries, [{creditedNolics: 3, creditedAtMs: 200}, {creditedNolics: 2, creditedAtMs: 200}]);
  assert.equal(typeof first.nextCursor, "string"); noPrivate(first);
  hiddenInCursor(first.nextCursor, "royalty-c", "PRIVATE_TICKET", "PRIVATE_PAYER");
  const second = await f.views.listMyRoyalty({uid: "author", payload: {pageSize: 2, cursor: first.nextCursor}});
  assert.deepEqual(second.entries, [{creditedNolics: 1, creditedAtMs: 100}]);
  assert.equal(second.nextCursor, null);
  await rejects(f.views.listMyRoyalty({uid: "other", payload: {cursor: first.nextCursor}}), "INVALID_ARGUMENT");
  assert.deepEqual(f.writes, []);
});

test("author outcome view binds exact submissions or bounded legacy drafts to Auth and never returns request bodies", async () => {
  const f = fixture();
  const exact = await f.views.listMyQuestReviewSubmissions({uid: "author", payload: {
    submissionIds: ["submission-rejected", "submission-published"], legacyDraftIds: [],
  }});
  assert.deepEqual(exact, {viewVersion: REVIEW_SUBMISSION_VIEW_VERSION, entries: [
    {submissionId: "submission-published", draftId: "draft-published", status: "PUBLISHED", rejectionReason: null,
      failedStages: [], questionOutcomes: [], failedQuestions: [], detectedLanguage: null, outcomeReady: false},
    {submissionId: "submission-rejected", draftId: "draft-rejected", status: "REJECTED",
      rejectionReason: "Correct the unsupported question format", failedStages: [], questionOutcomes: [],
      failedQuestions: [], detectedLanguage: null, outcomeReady: false},
  ]});
  noPrivate(exact);

  const legacy = await f.views.listMyQuestReviewSubmissions({uid: "author", payload: {
    submissionIds: [], legacyDraftIds: ["draft-legacy"],
  }});
  assert.deepEqual(legacy, {viewVersion: REVIEW_SUBMISSION_VIEW_VERSION, entries: [
    {submissionId: "legacy-current", draftId: "draft-legacy", status: "UNDER_REVIEW", rejectionReason: null,
      failedStages: [], questionOutcomes: [], failedQuestions: [], detectedLanguage: null, outcomeReady: false},
  ]});
  noPrivate(legacy);

  const hidden = await f.views.listMyQuestReviewSubmissions({uid: "author", payload: {
    submissionIds: ["submission-foreign"], legacyDraftIds: [],
  }});
  assert.deepEqual(hidden.entries, []);
  noPrivate(hidden); assert.deepEqual(f.writes, []);
});

test("author outcome view rejects malformed identity lists before reads and fails closed on a corrupt visible reason", async () => {
  const f = fixture();
  for (const payload of [
    {submissionIds: ["submission-published", "submission-published"], legacyDraftIds: []},
    {submissionIds: ["submission-published"], legacyDraftIds: ["draft-legacy".repeat(200)]},
    {submissionIds: Array.from({length: 20}, (_, index) => `submission-${index}`), legacyDraftIds: ["draft-legacy"]},
    {submissionIds: [], legacyDraftIds: [], uid: "other"},
  ]) await rejects(f.views.listMyQuestReviewSubmissions({uid: "author", payload}), "INVALID_ARGUMENT");
  assert.deepEqual(f.reads, []);

  f.documents.set("quest_review_requests/submission-rejected", reviewSubmission(
    "author", "draft-rejected", "REJECTED", 110, {rejectionReason: "x".repeat(1001)},
  ));
  await rejects(f.views.listMyQuestReviewSubmissions({uid: "author", payload: {
    submissionIds: ["submission-rejected"], legacyDraftIds: [],
  }}), "UNAVAILABLE");
  assert.deepEqual(f.writes, []);

  // По-вопросные исходы с чужим/битым составом не доезжают до автора: просьба была только про
  // названные заявки, а мусор в поле — generic unavailable, а не сфабрикованный DTO.
  f.documents.set("quest_review_requests/submission-in-review", reviewSubmission(
    "author", "draft-in-review", "UNDER_REVIEW", 105,
    {failedStages: ["TESTING"], questionOutcomes: [{questionId: "q1", testingScore: 9, passed: true}]},
  ));
  await rejects(f.views.listMyQuestReviewSubmissions({uid: "author", payload: {
    submissionIds: ["submission-in-review"], legacyDraftIds: [],
  }}), "UNAVAILABLE");
  f.documents.set("quest_review_requests/submission-in-review", reviewSubmission(
    "author", "draft-in-review", "UNDER_REVIEW", 105,
    {failedStages: ["MADE_UP_STAGE"], questionOutcomes: []},
  ));
  await rejects(f.views.listMyQuestReviewSubmissions({uid: "author", payload: {
    submissionIds: ["submission-in-review"], legacyDraftIds: [],
  }}), "UNAVAILABLE");
  assert.deepEqual(f.writes, []);
});

test("model review outcome fields reach the author with strict shapes and bounded lists (4.16)", async () => {
  const f = fixture();
  f.documents.set("quest_review_requests/submission-model", reviewSubmission(
    "author", "draft-model", "UNDER_REVIEW", 120, {
      reviewOutcome: {
        pipelineStatus: "QUEST_REVIEWED",
        failedQuestions: [
          {lessonId: "lesson-model", questionId: "q2", stage: "FACTS", reason: "ключ неверен: Париж"},
          {lessonId: "lesson-model", questionId: "q3", stage: "WORDS", reason: "запрещённый оборот"},
        ],
        detectedLanguage: "uk",
        passingLessonIds: ["lesson-model"],
        delayedStages: [],
        reviewedAtMs: 500,
      },
    },
  ));
  const reply = await f.views.listMyQuestReviewSubmissions({uid: "author", payload: {
    submissionIds: ["submission-model"], legacyDraftIds: [],
  }});
  assert.deepEqual(reply.entries[0].failedQuestions, [
    {lessonId: "lesson-model", questionId: "q2", stage: "FACTS", reason: "ключ неверен: Париж"},
    {lessonId: "lesson-model", questionId: "q3", stage: "WORDS", reason: "запрещённый оборот"},
  ]);
  assert.equal(reply.entries[0].detectedLanguage, "uk");
  assert.equal(reply.entries[0].outcomeReady, true, "UNDER_REVIEW + QUEST_REVIEWED asks the client to advance");
  // noPrivate здесь адресный: reason заваленного вопроса — авторское поле по UX-6 (расширение
  // списка), а приватным остаётся тело заявки и его служебные поля.
  const exposed = JSON.stringify(reply);
  for (const token of ["PRIVATE_", "\"questions\"", "processedAtMs", "lastError", "payload"]) {
    assert.equal(exposed.includes(token), false, token);
  }
  assert.deepEqual(f.writes, []);

  // Задержка: исход виден, довести до статуса не просят.
  const delayed = fixture();
  delayed.documents.set("quest_review_requests/submission-delayed", reviewSubmission(
    "author", "draft-delayed", "UNDER_REVIEW", 121, {
      reviewOutcome: {pipelineStatus: "REVIEW_DELAYED", failedQuestions: [], detectedLanguage: null,
        passingLessonIds: [], delayedStages: [{lessonId: "l", stage: "TRANSLATE"}], reviewedAtMs: 500},
    },
  ));
  const delayedReply = await delayed.views.listMyQuestReviewSubmissions({uid: "author", payload: {
    submissionIds: ["submission-delayed"], legacyDraftIds: [],
  }});
  assert.equal(delayedReply.entries[0].outcomeReady, false);
  assert.deepEqual(delayedReply.entries[0].failedQuestions, []);

  // Битые формы — generic unavailable: чужой этап, неизвестный язык, чужой submissionId,
  // слишком длинный список.
  for (const patch of [
    {reviewOutcome: {pipelineStatus: "QUEST_REVIEWED", failedQuestions: [
      {lessonId: "l", questionId: "q", stage: "MADE_UP", reason: "x"}], passingLessonIds: [], reviewedAtMs: 1}},
    {reviewOutcome: {pipelineStatus: "QUEST_REVIEWED", failedQuestions: [], detectedLanguage: "fr",
      passingLessonIds: [], reviewedAtMs: 1}},
    {reviewOutcome: {pipelineStatus: "WEIRD", failedQuestions: [], passingLessonIds: [], reviewedAtMs: 1}},
    {reviewOutcome: {pipelineStatus: "QUEST_REVIEWED",
      failedQuestions: Array.from({length: 201}, (_, i) =>
        ({lessonId: `l${i}`, questionId: "q", stage: "WORDS", reason: "x"})),
      passingLessonIds: [], reviewedAtMs: 1}},
  ]) {
    const g = fixture();
    g.documents.set("quest_review_requests/submission-model", reviewSubmission(
      "author", "draft-model", "UNDER_REVIEW", 120, patch,
    ));
    await rejects(g.views.listMyQuestReviewSubmissions({uid: "author", payload: {
      submissionIds: ["submission-model"], legacyDraftIds: [],
    }}), "UNAVAILABLE");
  }
  assert.deepEqual(f.writes, []);
});

test("a corrupt raw history or royalty row becomes a generic unavailable result rather than a fabricated DTO", async () => {
  const f = fixture();
  f.documents.set("lesson_placements/lesson-a/history/history-a", history(1, {code: "PRIVATE_CODE"}));
  await rejects(f.views.listHistory({uid: "author", payload: {lessonId: "lesson-a"}}), "UNAVAILABLE");
  const g = fixture();
  g.documents.set("audit/currency_ledger/entries/royalty-c", ledger("author", 200, 0));
  await rejects(g.views.listMyRoyalty({uid: "author", payload: {}}), "UNAVAILABLE");
  const h = fixture();
  h.documents.set("lesson_ratings/lesson-a", rating("lesson-a", {ratingSum: 1, ratingCount: 2}));
  await rejects(h.views.get({uid: "author", payload: {lessonId: "lesson-a"}}), "UNAVAILABLE");
  assert.deepEqual(f.writes, []); assert.deepEqual(g.writes, []); assert.deepEqual(h.writes, []);
});

test("a missing or malformed server cursor key fails closed instead of falling back to a reversible token", async () => {
  const f = fixture();
  f.views = createArenaLifecycleViews({
    db: f.db, loadLessonContext: f.loadLessonContext, isCurator: f.isCurator, readCursorKey: () => null,
  });
  await rejects(f.views.listHistory({uid: "author", payload: {lessonId: "lesson-a", pageSize: 1}}), "UNAVAILABLE");
  await rejects(f.views.listMyRoyalty({uid: "author", payload: {pageSize: 1}}), "UNAVAILABLE");
  assert.deepEqual(f.writes, []);
});
