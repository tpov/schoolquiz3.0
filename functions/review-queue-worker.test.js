"use strict";

// Воркер очереди проверки (истории 4.12/4.15): списывание очереди по одному квесту, идемпотентные
// вердикты по идентичности задания, DELAYED после трёх отказов линтера, resumption по lease,
// перевод прошедшего урока и SKIPPED для урока с FAIL. Гоняется на in-memory заглушке Firestore.

const assert = require("node:assert/strict");
const test = require("node:test");
const {createFirestoreStub} = require("./_firestore-stub");
const {QUEST_STATUS, verdictDocumentId, translationDocumentId} = require("./review-pipeline");
const {
  createReviewQueueWorker,
  PIPELINE_COLLECTION,
  TRANSLATIONS_COLLECTION,
  VERDICTS_COLLECTION,
} = require("./review-queue-worker");

const NOW = Date.parse("2026-10-26T00:00:00Z");

function choicePayload(id, text) {
  return JSON.stringify({
    type: "SingleChoice", id, difficulty: "EASY", text, imageUrl: null,
    options: [{id: "a", text: "три"}, {id: "b", text: "четыре"}], correctOptionId: "b",
  });
}

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
      {lessonId: "l1", id: "q1", text: "2+2?", payload: choicePayload("q1", "2+2?")},
      {lessonId: "l2", id: "q2", text: "3+3?", payload: choicePayload("q2", "3+3?")},
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

/** Промпты скиллов + поведение линтера: сколько первых ответов отвергнуть, какие вопросы завалить. */
function scriptedStages({lintFailures = 0, translateLintFailures = 0, failQuestions = []} = {}) {
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
            verdict: failQuestions.includes(question.id) ? "FAIL" : "PASS",
            reason: failQuestions.includes(question.id) ? "wrong answer" : "clean",
          })),
          sources: [],
          rationale: "ok",
        },
      };
    },
  });
  const translate = {
    promptVersion: "translate-v1",
    systemPrompt: () => "system-TRANSLATE",
    userPrompt: (context) => `user-TRANSLATE-${context.questTitle}`,
    lint: (content, context) => {
      if (translateLintFailures > 0) {
        translateLintFailures -= 1;
        return {ok: false, error: "TRANSLATION_STRUCTURE_MARKERS"};
      }
      const questions = {uk: [], en: []};
      for (const question of context.questions) {
        for (const language of ["uk", "en"]) {
          const payload = JSON.parse(question.payload);
          payload.text = `${language}:${payload.text}`;
          questions[language].push({
            ...question, id: `${question.id}__${language}`, language,
            text: payload.text, payload: JSON.stringify(payload),
          });
        }
      }
      return {ok: true, translation: {detectedLanguage: "ru", questions}};
    },
  };
  return {WORDS: build("WORDS"), FACTS: build("FACTS"), TRANSLATE: translate};
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

test("happy drain: one sweep finishes a whole quest with immutable verdicts and translations", async () => {
  const {admin, store} = createFirestoreStub(seedDocs({
    "catalogs/cat-1": {name: "Математика"},
    "quest_review_requests/sub-1": submission("1"),
  }));
  const db = admin.firestore();
  const calls = [];
  const result = await worker(db, scriptedStages({}), {maxStageCallsPerSweep: 12},
    scriptedModelClient({calls})).sweep();

  assert.equal(result.status, "PROCESSED");
  assert.equal(calls.length, 6, "two lessons x three stages = six model calls");
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
    // Перевод прошедшего урока: одна неизменяемая запись с обоими целевыми языками (4.15).
    const translationId = translationDocumentId({submissionId: "sub-1", lessonId, attempt: 1});
    const translation = store.documents.get(`${TRANSLATIONS_COLLECTION}/${translationId}`);
    assert.ok(translation, `missing translation ${translationId}`);
    assert.equal(translation.model, "glm-test");
    assert.equal(translation.promptVersion, "translate-v1");
    assert.equal(translation.detectedLanguage, "ru");
    assert.deepEqual(Object.keys(translation.questions).sort(), ["en", "uk"]);
    assert.equal(translation.questions.uk[0].id, `${lessonId === "l1" ? "q1" : "q2"}__uk`);
    assert.equal(pipeline.lessons[lessonId].stages.TRANSLATE.state, "DONE");
  }
});

test("a lesson with a FAIL question skips translation: no call, no record, quest still reviewed", async () => {
  const {admin, store} = createFirestoreStub(seedDocs({
    "catalogs/cat-1": {name: "Математика"},
    "quest_review_requests/sub-1": submission("1"),
  }));
  const db = admin.firestore();
  const calls = [];
  const stages = scriptedStages({failQuestions: ["q2"]});
  const result = await worker(db, stages, {maxStageCallsPerSweep: 12},
    scriptedModelClient({calls})).sweep();

  assert.equal(result.status, "PROCESSED");
  // l1: Слова, Факты, Перевод; l2: Слова, Факты — перевод провалившегося урока не вызывается.
  assert.equal(calls.length, 5);
  const pipeline = store.documents.get(`${PIPELINE_COLLECTION}/sub-1`);
  assert.equal(pipeline.status, QUEST_STATUS.REVIEWED);
  assert.equal(pipeline.lessons.l1.stages.TRANSLATE.state, "DONE");
  assert.equal(pipeline.lessons.l2.stages.TRANSLATE.state, "SKIPPED");
  assert.equal(pipeline.lessons.l2.stages.TRANSLATE.attempts, 0);
  const translations = [...store.documents.keys()]
    .filter((path) => path.startsWith(`${TRANSLATIONS_COLLECTION}/sub-1_`));
  assert.deepEqual(translations, [`${TRANSLATIONS_COLLECTION}/sub-1_l1_TRANSLATE_1`],
    "only the passing lesson is translated");
});

test("three translation linter refusals land the lesson REVIEW_DELAYED with zero records", async () => {
  const {admin, store} = createFirestoreStub(seedDocs({
    "catalogs/cat-1": {name: "Математика"},
    "quest_review_requests/sub-1": submission("1"),
  }));
  const db = admin.firestore();
  const stages = scriptedStages({translateLintFailures: 3});
  const result = await worker(db, stages, {maxStageCallsPerSweep: 12}).sweep();

  assert.equal(result.status, "PROCESSED");
  const pipeline = store.documents.get(`${PIPELINE_COLLECTION}/sub-1`);
  assert.equal(pipeline.lessons.l1.stages.TRANSLATE.state, "DELAYED");
  assert.deepEqual(pipeline.lessons.l1.stages.TRANSLATE.errors,
    ["TRANSLATION_STRUCTURE_MARKERS", "TRANSLATION_STRUCTURE_MARKERS", "TRANSLATION_STRUCTURE_MARKERS"]);
  assert.equal(pipeline.status, QUEST_STATUS.DELAYED);
  const forL1 = [...store.documents.keys()]
    .filter((path) => path.startsWith(`${TRANSLATIONS_COLLECTION}/sub-1_l1_TRANSLATE`));
  assert.deepEqual(forL1, [], "a delayed translation records nothing");
});

test("terminal quest writes the author outcome into the submission (4.16)", async () => {
  const {admin, store} = createFirestoreStub(seedDocs({
    "catalogs/cat-1": {name: "Математика"},
    "quest_review_requests/sub-1": submission("1"),
  }));
  const db = admin.firestore();
  await worker(db, scriptedStages({}), {maxStageCallsPerSweep: 12}).sweep();

  const outcome = store.documents.get("quest_review_requests/sub-1").reviewOutcome;
  assert.ok(outcome, "the terminal sweep records the author outcome");
  assert.equal(outcome.pipelineStatus, "QUEST_REVIEWED");
  assert.deepEqual(outcome.passingLessonIds, ["l1", "l2"]);
  assert.deepEqual(outcome.failedQuestions, []);
  assert.equal(outcome.detectedLanguage, "ru");
  assert.ok(outcome.reviewedAtMs > 0);
});

test("a failed question reaches the outcome with lesson, stage and reason (UX-6)", async () => {
  const {admin, store} = createFirestoreStub(seedDocs({
    "catalogs/cat-1": {name: "Математика"},
    "quest_review_requests/sub-1": submission("1"),
  }));
  const db = admin.firestore();
  await worker(db, scriptedStages({failQuestions: ["q2"]}), {maxStageCallsPerSweep: 12}).sweep();

  const outcome = store.documents.get("quest_review_requests/sub-1").reviewOutcome;
  assert.equal(outcome.pipelineStatus, "QUEST_REVIEWED");
  assert.deepEqual(outcome.passingLessonIds, ["l1"]);
  // Скриптованные проверки валят q2 на обоих этапах: исход несёт по записи на каждый этап (UX-6).
  assert.deepEqual(outcome.failedQuestions, [
    {lessonId: "l2", questionId: "q2", stage: "WORDS", reason: "wrong answer"},
    {lessonId: "l2", questionId: "q2", stage: "FACTS", reason: "wrong answer"},
  ]);
});

test("a delayed stage writes REVIEW_DELAYED outcome with the delayed stage named", async () => {
  const {admin, store} = createFirestoreStub(seedDocs({
    "catalogs/cat-1": {name: "Математика"},
    "quest_review_requests/sub-1": submission("1"),
  }));
  const db = admin.firestore();
  await worker(db, scriptedStages({translateLintFailures: 3}), {maxStageCallsPerSweep: 12}).sweep();

  const outcome = store.documents.get("quest_review_requests/sub-1").reviewOutcome;
  assert.equal(outcome.pipelineStatus, "REVIEW_DELAYED");
  // Лимит отказов общий на попытки: l1 сжёг три и задержался, l2 тем же скиллом прошёл.
  assert.deepEqual(outcome.delayedStages, [{lessonId: "l1", stage: "TRANSLATE"}]);
  assert.deepEqual(outcome.passingLessonIds, ["l2"]);
  assert.deepEqual(outcome.detectedLanguage, "ru");
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
  const translateEntry = () => ({state: "PENDING", attempts: 0, errors: [], verdictId: null});
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
          TRANSLATE: translateEntry(),
        }},
        l2: {overall: "PENDING", stages: {
          WORDS: {state: "PENDING", attempts: 0, errors: [], verdictId: null},
          FACTS: {state: "PENDING", attempts: 0, errors: [], verdictId: null},
          TRANSLATE: translateEntry(),
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
  // Задание (l1, WORDS, 1) уже имеет вердикт: модель вызывается только по пяти оставшимся
  // (l1 Факты+Перевод, l2 Слова+Факты+Перевод).
  assert.equal(calls.length, 5, "the finished job is never re-judged");
  assert.equal(store.documents.get(`${VERDICTS_COLLECTION}/${verdictId}`).questionVerdicts[0].reason, "clean");
  assert.equal(store.documents.get(`${PIPELINE_COLLECTION}/sub-1`).status, QUEST_STATUS.REVIEWED);
  const verdictCount = [...store.documents.keys()].filter((path) => path.startsWith(`${VERDICTS_COLLECTION}/sub-1_`)).length;
  assert.equal(verdictCount, 4, "exactly one verdict per (lesson, stage, attempt)");
  const translationCount = [...store.documents.keys()]
    .filter((path) => path.startsWith(`${TRANSLATIONS_COLLECTION}/sub-1_`)).length;
  assert.equal(translationCount, 2, "one translation per lesson");
});

test("crash after a translation write resumes idempotently: record kept, no second call", async () => {
  const translationId = translationDocumentId({submissionId: "sub-1", lessonId: "l1", attempt: 1});
  const stage = (state, attempts, verdictId = null) => ({state, attempts, errors: [], verdictId});
  const {admin, store} = createFirestoreStub(seedDocs({
    "catalogs/cat-1": {name: "Математика"},
    "quest_review_requests/sub-1": submission("1"),
    [`${VERDICTS_COLLECTION}/sub-1_l1_WORDS_1`]: {
      schemaVersion: 1, submissionId: "sub-1", lessonId: "l1", stage: "WORDS", attempt: 1,
      revision: 5, model: "glm-test", promptVersion: "words-v1", sources: [], rationale: "ok",
      questionVerdicts: [{questionId: "q1", verdict: "PASS", reason: "clean"}],
    },
    [`${VERDICTS_COLLECTION}/sub-1_l1_FACTS_1`]: {
      schemaVersion: 1, submissionId: "sub-1", lessonId: "l1", stage: "FACTS", attempt: 1,
      revision: 5, model: "glm-test", promptVersion: "facts-v1", sources: [], rationale: "ok",
      questionVerdicts: [{questionId: "q1", verdict: "PASS", reason: "clean"}],
    },
    [`${TRANSLATIONS_COLLECTION}/${translationId}`]: {
      schemaVersion: 1, submissionId: "sub-1", lessonId: "l1", stage: "TRANSLATE", attempt: 1,
      revision: 5, model: "glm-test", promptVersion: "translate-v1", detectedLanguage: "ru",
      questions: {uk: [{id: "q1__uk"}], en: [{id: "q1__en"}]},
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
          WORDS: stage("DONE", 1, "sub-1_l1_WORDS_1"),
          FACTS: stage("DONE", 1, "sub-1_l1_FACTS_1"),
          TRANSLATE: {state: "PENDING", attempts: 1, errors: [], verdictId: null},
        }},
        l2: {overall: "PENDING", stages: {
          WORDS: stage("PENDING", 0),
          FACTS: stage("PENDING", 0),
          TRANSLATE: stage("PENDING", 0),
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
  // Запись перевода l1 уже стоит: вызовов — только l2 (Слова, Факты, Перевод).
  assert.equal(calls.length, 3, "the written translation is never re-judged");
  const pipeline = store.documents.get(`${PIPELINE_COLLECTION}/sub-1`);
  assert.equal(pipeline.lessons.l1.stages.TRANSLATE.state, "DONE");
  assert.equal(pipeline.lessons.l1.stages.TRANSLATE.verdictId, translationId);
  assert.equal(pipeline.status, QUEST_STATUS.REVIEWED);
  const l1Translations = [...store.documents.keys()]
    .filter((path) => path.startsWith(`${TRANSLATIONS_COLLECTION}/sub-1_l1_TRANSLATE`));
  assert.deepEqual(l1Translations, [`${TRANSLATIONS_COLLECTION}/${translationId}`]);
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

// ─── Предложения (4.21, AD-33): вторая семья того же свипа ─────────────────────────────────────

const {
  PROPOSAL_PIPELINE_COLLECTION,
  PROPOSAL_VERDICTS_COLLECTION,
} = require("./review-queue-worker");
const {PROPOSALS_COLLECTION} = require("./lesson-proposals");

function proposalRecord(id, extra = {}) {
  return {
    schemaVersion: 1,
    proposalId: id,
    kind: "REPORT",
    proposerUid: "proposer-1",
    targetOwnerUid: "author-1",
    target: {lessonId: "lesson_1"},
    baseRevision: 3,
    payload: {text: "В уроке ошибка"},
    fee: {nolics: 0, changedQuestions: 0, tariffRevision: 1, chargedAtMs: 0},
    status: "QUEUED",
    reviewOutcome: null,
    checkedAtMs: 0,
    decidedAtMs: 0,
    createdAtMs: NOW,
    updatedAtMs: NOW,
    ...extra,
  };
}

function proposalWorld(id, record = {}) {
  return {[`${PROPOSALS_COLLECTION}/${id}`]: proposalRecord(id, record)};
}

test("a QUEUED proposal is checked by the same skills into its own verdict collection", async () => {
  const {admin, store} = createFirestoreStub(proposalWorld("prop-1"));
  const db = admin.firestore();
  const calls = [];
  const result = await worker(db, scriptedStages({}), {maxStageCallsPerSweep: 12},
    scriptedModelClient({calls})).sweep();

  assert.equal(result.status, "PROCESSED");
  assert.equal(calls.length, 2, "one unit x two check stages");
  const record = store.documents.get(`${PROPOSALS_COLLECTION}/prop-1`);
  assert.equal(record.status, "CHECK_PASSED");
  assert.deepEqual(record.reviewOutcome.failedQuestions, []);
  assert.deepEqual(record.reviewOutcome.passingLessonIds, ["lesson_1"]);
  assert.equal(record.checkedAtMs, NOW);
  // Свой мир: конвейер и вердикты предложений не текут в мир заявок.
  assert.ok(store.documents.get(`${PROPOSAL_PIPELINE_COLLECTION}/prop-1`));
  assert.equal([...store.documents.keys()].filter((path) => path.startsWith(`${VERDICTS_COLLECTION}/`)).length, 0);
  const proposalVerdicts = [...store.documents.keys()]
    .filter((path) => path.startsWith(`${PROPOSAL_VERDICTS_COLLECTION}/`));
  assert.equal(proposalVerdicts.length, 2, "WORDS and FACTS verdicts are immutable records");
  assert.ok(proposalVerdicts.every((path) => path.includes("prop-1_lesson_1_")));
});

test("a FAIL question fails the proposal, never reaching the author state", async () => {
  const {admin, store} = createFirestoreStub(proposalWorld("prop-fail"));
  const db = admin.firestore();
  await worker(db, scriptedStages({failQuestions: ["report"]}), {maxStageCallsPerSweep: 12}).sweep();
  const record = store.documents.get(`${PROPOSALS_COLLECTION}/prop-fail`);
  assert.equal(record.status, "CHECK_FAILED");
  // Оба проверочных этапа судят одну единицу: FAIL вопроса едет из каждого своей строкой.
  assert.deepEqual(record.reviewOutcome.failedQuestions.map((entry) => entry.stage), ["WORDS", "FACTS"]);
  assert.ok(record.reviewOutcome.failedQuestions.every((entry) => entry.questionId === "report"));
});

test("exhausted linter retries delay the proposal without a guessed verdict", async () => {
  const {admin, store} = createFirestoreStub(proposalWorld("prop-lint"));
  const db = admin.firestore();
  await worker(db, scriptedStages({lintFailures: 6}), {maxStageCallsPerSweep: 12}).sweep();
  const record = store.documents.get(`${PROPOSALS_COLLECTION}/prop-lint`);
  assert.equal(record.status, "CHECK_DELAYED");
  // Счётчик отказов общий на оба этапа: по три попытки каждому — задержаны оба.
  assert.deepEqual(record.reviewOutcome.delayedStages,
    [{lessonId: "lesson_1", stage: "WORDS"}, {lessonId: "lesson_1", stage: "FACTS"}]);
});

test("terminal proposals are skipped and never re-checked", async () => {
  const checked = proposalWorld("prop-done", {status: "CHECK_PASSED"});
  checked[`${PROPOSAL_PIPELINE_COLLECTION}/prop-done`] = {
    schemaVersion: 1, submissionId: "prop-done", status: "QUEST_REVIEWED",
    stages: ["WORDS", "FACTS"], revision: 3,
    lessons: {lesson_1: {stages: {WORDS: {state: "DONE"}, FACTS: {state: "DONE"}}}},
  };
  const {admin, store} = createFirestoreStub(checked);
  const db = admin.firestore();
  const calls = [];
  const result = await worker(db, scriptedStages({}), {maxStageCallsPerSweep: 12},
    scriptedModelClient({calls})).sweep();
  assert.equal(calls.length, 0, "terminal proposals cost nothing");
  assert.equal(result.status, "IDLE");
  assert.equal(store.documents.get(`${PROPOSALS_COLLECTION}/prop-done`).status, "CHECK_PASSED");
});

test("submissions go first: a full budget leaves the proposal for the next sweep", async () => {
  const world = {
    "catalogs/cat-1": {name: "Математика"},
    "quest_review_requests/sub-1": submission("1"),
    ...proposalWorld("prop-2"),
  };
  const {admin, store} = createFirestoreStub(world);
  const db = admin.firestore();
  const result = await worker(db, scriptedStages({}), {maxStageCallsPerSweep: 2}).sweep();
  // Два этапа первого урока съели бюджет: предложение осталось QUEUED.
  assert.equal(result.status, "TIME_BUDGET");
  assert.equal(store.documents.get(`${PROPOSALS_COLLECTION}/prop-2`).status, "QUEUED");
});

test("a proposal payload that lost its content is reported, not judged", async () => {
  const broken = proposalWorld("prop-broken", {payload: {}});
  const {admin, store} = createFirestoreStub(broken);
  const db = admin.firestore();
  const logs = [];
  const failing = createReviewQueueWorker({
    db,
    stages: scriptedStages({}),
    modelClient: {modelName: "glm-test", judge: async () => ({ok: true, content: "{}"})},
    nowMs: () => NOW,
    log: (entry) => logs.push(entry),
  });
  const result = await failing.sweep();
  assert.equal(result.status, "IDLE", "the broken candidate is skipped, the sweep continues");
  assert.ok(logs.some((entry) => entry.code === "PROPOSAL_CLAIM_FAILED" ||
    entry.code === "PROPOSAL_SOURCE_UNREADABLE"));
  assert.equal(store.documents.get(`${PROPOSALS_COLLECTION}/prop-broken`).status, "QUEUED");
});
