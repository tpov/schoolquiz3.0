"use strict";

// Воркер очереди проверки (история 4.12): списывание очереди по одному квесту, идемпотентные
// вердикты по идентичности задания, DELAYED после трёх отказов линтера, resumption по lease.
// Гоняется на in-memory заглушке Firestore — как и весь индексный состав.

const assert = require("node:assert/strict");
const test = require("node:test");
const {createFirestoreStub} = require("./_firestore-stub");
const {QUEST_STATUS, verdictDocumentId} = require("./review-pipeline");
const {createReviewQueueWorker, PIPELINE_COLLECTION, VERDICTS_COLLECTION} = require("./review-queue-worker");

const NOW = Date.parse("2026-10-26T00:00:00Z");

function submission(id, extra = {}) {
  return {
    status: "UNDER_REVIEW",
    requestedAtMs: NOW,
    ownerUid: "author-1",
    localRevision: 5,
    targetShelf: "arena",
    draft: {id: `quest-${id}`, catalogId: "cat-1", title: "Квест", defaultLanguage: "ru"},
    lessons: [{id: "l1", title: "Урок 1"}, {id: "l2", title: "Урок 2"}],
    questions: [
      {lessonId: "l1", id: "q1", text: "2+2?"},
      {lessonId: "l2", id: "q2", text: "3+3?"},
    ],
    ...extra,
  };
}

function scriptedModelClient({calls = [], replies = []} = {}) {
  return {
    modelName: "glm-test",
    judge: async () => {
      const index = calls.length;
      calls.push(index);
      const reply = replies[index] || {ok: true, content: "{}"};
      return reply;
    },
  };
}

/** Промпты скиллов + поведение линтера: сколько первых ответов отвергнуть. */
function scriptedStages({lintFailures = 0} = {}) {
  const build = (stage) => ({
    promptVersion: `${stage.toLowerCase()}-v1`,
    systemPrompt: () => `system-${stage}`,
    userPrompt: (context) => `user-${stage}-${context.questTitle}`,
    lint: (content, context) => {
      if (lintFailures > 0) {
        lintFailures -= 1;
        return {ok: false, error: "REVIEW_SCHEMA_BAD_SHAPE"};
      }
      return {
        ok: true,
        verdict: {
          questionVerdicts: context.questions.map((question) => ({
            questionId: question.id,
            verdict: "PASS",
            reason: "clean",
          })),
          sources: [],
          rationale: "ok",
        },
      };
    },
  });
  return {WORDS: build("WORDS"), FACTS: build("FACTS")};
}

function worker(db, stages, options = {}, modelClient = undefined) {
  return createReviewQueueWorker({
    db,
    stages,
    modelClient: modelClient || {modelName: "glm-test", judge: async () => ({ok: true, content: "{}"})},
    nowMs: () => NOW,
    log: () => {},
    ...options,
  });
}

function seedDocs(world) {
  const seed = {};
  for (const [path, document] of Object.entries(world)) seed[path] = document;
  return seed;
}

test("happy drain: one sweep finishes a whole quest with immutable verdicts", async () => {
  const {admin, store} = createFirestoreStub(seedDocs({
    "catalogs/cat-1": {name: "Математика"},
    "quest_review_requests/sub-1": submission("1"),
  }));
  const db = admin.firestore();
  const calls = [];
  const result = await worker(db, scriptedStages({}), {maxStageCallsPerSweep: 8},
    scriptedModelClient({calls})).sweep();

  assert.equal(result.status, "PROCESSED");
  assert.equal(calls.length, 4, "two lessons x two stages = four judging calls");
  const pipeline = store.documents.get(`${PIPELINE_COLLECTION}/sub-1`);
  assert.equal(pipeline.status, QUEST_STATUS.REVIEWED);
  assert.equal(pipeline.questTitle, "Квест");
  assert.equal(pipeline.categoryPath, "Математика");
  // Два урока × два этапа = четыре вердикта, каждый неизменяемым документом.
  for (const lessonId of ["l1", "l2"]) {
    for (const stage of ["WORDS", "FACTS"]) {
      const id = `sub-1_${lessonId}_${stage}_1`;
      const verdict = store.documents.get(`${VERDICTS_COLLECTION}/${id}`);
      assert.ok(verdict, `missing verdict ${id}`);
      assert.equal(verdict.model, "glm-test");
      assert.equal(verdict.promptVersion, `${stage.toLowerCase()}-v1`);
      assert.equal(verdict.revision, 5);
      assert.ok(verdict.questionVerdicts.length >= 1);
      // Заявка остаётся в очереди: публикация — история 4.16.
      assert.equal(store.documents.get("quest_review_requests/sub-1").status, "UNDER_REVIEW");
    }
  }
});

test("linter rejects three times, then the stage lands REVIEW_DELAYED with zero verdicts", async () => {
  const {admin, store} = createFirestoreStub(seedDocs({
    "catalogs/cat-1": {name: "Математика"},
    "quest_review_requests/sub-1": submission("1"),
  }));
  const db = admin.firestore();
  const result = await worker(db, scriptedStages({lintFailures: 3}), {maxStageCallsPerSweep: 8}).sweep();

  assert.equal(result.status, "PROCESSED");
  const pipeline = store.documents.get(`${PIPELINE_COLLECTION}/sub-1`);
  assert.equal(pipeline.lessons.l1.stages.WORDS.state, "DELAYED");
  assert.equal(pipeline.lessons.l1.stages.WORDS.attempts, 3);
  assert.deepEqual(pipeline.lessons.l1.stages.WORDS.errors,
    ["REVIEW_SCHEMA_BAD_SHAPE", "REVIEW_SCHEMA_BAD_SHAPE", "REVIEW_SCHEMA_BAD_SHAPE"]);
  assert.equal(pipeline.status, QUEST_STATUS.DELAYED);
  const verdicts = [...store.documents.keys()].filter((path) => path.startsWith(`${VERDICTS_COLLECTION}/sub-1_l1_WORDS`));
  assert.deepEqual(verdicts, [], "a delayed stage records no verdict");
});

test("model transport failure is not an attempt: quest stays queued, nothing written", async () => {
  const {admin, store} = createFirestoreStub(seedDocs({
    "catalogs/cat-1": {name: "Математика"},
    "quest_review_requests/sub-1": submission("1"),
  }));
  const db = admin.firestore();
  const model = scriptedModelClient({replies: [{ok: false, code: "REVIEW_MODEL_UNAVAILABLE", message: "down"}]});
  const result = await worker(db, scriptedStages({}), {maxStageCallsPerSweep: 8}, model).sweep();

  assert.equal(result.status, "MODEL_UNAVAILABLE");
  const pipeline = store.documents.get(`${PIPELINE_COLLECTION}/sub-1`);
  assert.equal(pipeline.status, QUEST_STATUS.RUNNING);
  assert.equal(pipeline.lessons.l1.stages.WORDS.attempts, 0);
  assert.equal([...store.documents.keys()].filter((path) => path.startsWith(`${VERDICTS_COLLECTION}/`)).length, 0);
});

test("crash resumption: expired lease resumes, finished verdicts survive without a second call", async () => {
  const verdictId = verdictDocumentId({submissionId: "sub-1", lessonId: "l1", stage: "WORDS", attempt: 1});
  const {admin, store} = createFirestoreStub(seedDocs({
    "catalogs/cat-1": {name: "Математика"},
    "quest_review_requests/sub-1": submission("1"),
    [`${VERDICTS_COLLECTION}/${verdictId}`]: {
      schemaVersion: 1, submissionId: "sub-1", lessonId: "l1", stage: "WORDS", attempt: 1,
      revision: 5, model: "glm-test", promptVersion: "words-v1", sources: [], rationale: "ok",
      questionVerdicts: [{questionId: "q1", verdict: "PASS", reason: "clean"}],
    },
    [`${PIPELINE_COLLECTION}/sub-1`]: {
      schemaVersion: 1,
      submissionId: "sub-1",
      ownerUid: "author-1",
      questId: "quest-1",
      questTitle: "Квест",
      categoryPath: "Математика",
      revision: 5,
      status: QUEST_STATUS.RUNNING,
      lease: {ownerNonce: "a".repeat(48), expiresAtMs: NOW - 1000},
      lessons: {
        l1: {overall: "PENDING", stages: {
          WORDS: {state: "PENDING", attempts: 1, errors: [], verdictId: null},
          FACTS: {state: "PENDING", attempts: 0, errors: [], verdictId: null},
        }},
        l2: {overall: "PENDING", stages: {
          WORDS: {state: "PENDING", attempts: 0, errors: [], verdictId: null},
          FACTS: {state: "PENDING", attempts: 0, errors: [], verdictId: null},
        }},
      },
      createdAtMs: NOW - 600_000,
      updatedAtMs: NOW - 600_000,
    },
  }));
  const db = admin.firestore();
  const calls = [];
  const result = await worker(db, scriptedStages({}), {maxStageCallsPerSweep: 8},
    scriptedModelClient({calls})).sweep();

  assert.equal(result.status, "PROCESSED");
  // Задание (l1, WORDS, 1) уже имеет вердикт: модель вызывается только по трём оставшимся.
  assert.equal(calls.length, 3, "the finished job is never re-judged");
  assert.equal(store.documents.get(`${VERDICTS_COLLECTION}/${verdictId}`).questionVerdicts[0].reason, "clean");
  assert.equal(store.documents.get(`${PIPELINE_COLLECTION}/sub-1`).status, QUEST_STATUS.REVIEWED);
  const verdictCount = [...store.documents.keys()].filter((path) => path.startsWith(`${VERDICTS_COLLECTION}/sub-1_`)).length;
  assert.equal(verdictCount, 4, "exactly one verdict per (lesson, stage, attempt)");
});

test("a held lease makes the next sweep BUSY without writing anything", async () => {
  const {admin, store} = createFirestoreStub(seedDocs({
    "review_worker_state/sweep": {
      schemaVersion: 1,
      lease: {ownerNonce: "b".repeat(48), acquiredAtMs: NOW - 1000, expiresAtMs: NOW + 600_000},
      updatedAtMs: NOW - 1000,
    },
    "quest_review_requests/sub-1": submission("1"),
  }));
  const db = admin.firestore();
  const result = await worker(db, scriptedStages({})).sweep();
  assert.equal(result.status, "BUSY");
  assert.equal(store.documents.get(`${PIPELINE_COLLECTION}/sub-1`), undefined);
});

test("unregistered stages refuse to touch the queue", async () => {
  const {admin, store} = createFirestoreStub(seedDocs({
    "quest_review_requests/sub-1": submission("1"),
  }));
  const db = admin.firestore();
  const result = await worker(db, {WORDS: scriptedStages({}).WORDS}).sweep();
  assert.equal(result.status, "STAGE_MISSING");
  assert.equal(store.documents.get(`${PIPELINE_COLLECTION}/sub-1`), undefined);
});

test("empty queue sweeps IDLE", async () => {
  const {admin} = createFirestoreStub(seedDocs({}));
  const result = await worker(admin.firestore(), scriptedStages({})).sweep();
  assert.equal(result.status, "IDLE");
  assert.equal(result.attempted, 0);
});

test("an unparsable submission is skipped, not fatal, and claims nothing", async () => {
  const {admin, store} = createFirestoreStub(seedDocs({
    "quest_review_requests/sub-bad": submission("bad", {lessons: []}),
  }));
  const db = admin.firestore();
  const result = await worker(db, scriptedStages({}), {maxStageCallsPerSweep: 8}).sweep();
  assert.equal(result.status, "IDLE");
  assert.equal(store.documents.get(`${PIPELINE_COLLECTION}/sub-bad`), undefined);
});
