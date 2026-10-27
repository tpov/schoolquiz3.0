"use strict";

/**
 * Воркер очереди проверки (AD-32, история 4.12).
 *
 * Отдельный процесс на VPS, по образцу `arena-season-worker` (AD-23): durable lease, ограниченные
 * шаги, чекпойнт в документе состояния, никакой клиентской власти. Не триггер функции: судящий
 * вызов модели с поиском длится минутами.
 *
 * Один квест за раз, уроки по одному. Прогресс живёт в `review_pipeline/{submissionId}` —
 * единственном возобновляемом чекпойнте; вердикты пишутся отдельными неизменяемыми документами
 * с детерминированным именем, поэтому повтор шага вердикта не плодит. Урок передаётся модели
 * данными между разграничителями; ответ судит линтер скилла, а не доверие форме.
 */

const {createHash, randomBytes} = require("node:crypto");
const {
  QUEST_STATUS,
  STAGE_PLAN,
  applyStageError,
  applyStageSkip,
  applyVerdict,
  buildTranslationRecord,
  buildVerdictRecord,
  createPipelineState,
  nextJob,
  translationDocumentId,
  verdictDocumentId,
} = require("./review-pipeline");
const {wrapLessonData} = require("./review-model-client");

const WORKER_STATE_COLLECTION = "review_worker_state";
const WORKER_STATE_PATH = `${WORKER_STATE_COLLECTION}/sweep`;
const PIPELINE_COLLECTION = "review_pipeline";
const VERDICTS_COLLECTION = "review_verdicts";
const TRANSLATIONS_COLLECTION = "review_translations";
const QUEUE_COLLECTION = "quest_review_requests";
const LEASE_MS = 660_000;
/** Судящий вызов занимает минуты: один шаг свипа — один вызов этапа, и свип на этом честно кончается. */
const MAX_STAGE_CALLS_PER_SWEEP = 2;
const CANDIDATE_PAGE = 20;

function fail(code, message = code) {
  const error = new Error(message);
  error.code = code;
  throw error;
}

function data(snapshot) {
  return snapshot && snapshot.exists ? snapshot.data() || {} : null;
}

function stateRecord(raw) {
  if (!raw) return {schemaVersion: 1, lease: null};
  if (raw.schemaVersion !== 1 ||
    (raw.lease !== null && (!raw.lease || !/^[a-f0-9]{48}$/.test(raw.lease.ownerNonce || "") ||
      !Number.isSafeInteger(raw.lease.acquiredAtMs) || raw.lease.acquiredAtMs < 0 ||
      !Number.isSafeInteger(raw.lease.expiresAtMs) || raw.lease.expiresAtMs <= raw.lease.acquiredAtMs))) {
    fail("UNAVAILABLE", "Review worker state requires repair");
  }
  return raw;
}

function pipelineRecord(raw) {
  if (!raw || raw.schemaVersion !== 1 || typeof raw.submissionId !== "string" ||
    typeof raw.status !== "string") {
    fail("UNAVAILABLE", "Review pipeline record requires repair");
  }
  return raw;
}

/**
 * Один зарегистрированный скилл этапа: промпт с версией и линтер ответа (истории 4.13/4.14).
 *
 * Скилл не вызывает модель сам: судящий вызов делает воркер общим клиентом, скилл владеет
 * промптом и разбором ответа — так транспорт и ключ остаются в одном месте.
 */
function requireStageSkill(stages, stage) {
  const skill = stages && stages[stage];
  if (!skill || typeof skill.systemPrompt !== "function" || typeof skill.userPrompt !== "function" ||
    typeof skill.lint !== "function" ||
    typeof skill.promptVersion !== "string" || !skill.promptVersion) {
    fail("STAGE_MISSING", `review stage ${stage} has no registered skill`);
  }
  return skill;
}

/**
 * Создаёт воркер. Зависимости вводятся снаружи: `db` — admin Firestore (в тестах — заглушка),
 * `stages` — реестр скиллов, `modelClient` — транспорт модели (см. review-model-client).
 */
function createReviewQueueWorker({
  db,
  stages,
  modelClient,
  nowMs = Date.now,
  log = () => {},
  leaseMs = LEASE_MS,
  maxStageCallsPerSweep = MAX_STAGE_CALLS_PER_SWEEP,
  candidatePage = CANDIDATE_PAGE,
  statePath: trustedStatePath = WORKER_STATE_PATH,
}) {
  if (!db || typeof db.runTransaction !== "function" || !stages || !modelClient ||
    typeof modelClient.judge !== "function" || typeof nowMs !== "function" || typeof log !== "function") {
    throw new TypeError("Trusted review queue worker dependencies required");
  }
  if (!Number.isSafeInteger(leaseMs) || leaseMs <= 0 ||
    !Number.isSafeInteger(maxStageCallsPerSweep) || maxStageCallsPerSweep < 1 ||
    !Number.isSafeInteger(candidatePage) || candidatePage < 1) {
    fail("INVALID_ARGUMENT", "Review worker bounds are invalid");
  }
  const [stateCollection, stateId, ...stateRest] = trustedStatePath.split("/");
  if (stateCollection !== WORKER_STATE_COLLECTION || stateRest.length !== 0 || !stateId ||
    stateId.includes("/")) {
    fail("INVALID_ARGUMENT", "Review worker state path is outside its private root");
  }
  const stateRef = db.doc(trustedStatePath);
  const now = () => nowMs();
  const owns = (state, nonce, at) => Boolean(state.lease) && state.lease.ownerNonce === nonce &&
    state.lease.expiresAtMs > at;

  function report(submissionId, code) {
    // В журнал идёт только хеш адреса: ни текста урока, ни вердикта, ни ключа.
    log({component: "review-queue-worker", code,
      submissionReference: createHash("sha256").update(String(submissionId || "")).digest("hex")});
  }

  async function acquire(nonce) {
    return db.runTransaction(async (tx) => {
      const state = stateRecord(data(await tx.get(stateRef)));
      const at = now();
      if (state.lease && state.lease.expiresAtMs > at) return {acquired: false, retryAtMs: state.lease.expiresAtMs};
      tx.set(stateRef, {schemaVersion: 1, lease: {ownerNonce: nonce, acquiredAtMs: at, expiresAtMs: at + leaseMs}, updatedAtMs: at});
      return {acquired: true};
    });
  }

  async function checkpoint(nonce) {
    return db.runTransaction(async (tx) => {
      const state = stateRecord(data(await tx.get(stateRef)));
      const at = now();
      if (!owns(state, nonce, at)) return false;
      tx.set(stateRef, {...state, lease: {ownerNonce: nonce, acquiredAtMs: state.lease.acquiredAtMs, expiresAtMs: at + leaseMs}, updatedAtMs: at});
      return true;
    });
  }

  async function release(nonce) {
    return db.runTransaction(async (tx) => {
      const state = stateRecord(data(await tx.get(stateRef)));
      const at = now();
      if (!owns(state, nonce, at)) return false;
      tx.set(stateRef, {...state, lease: null, updatedAtMs: at});
      return true;
    });
  }

  /** Кандидаты: принятые и неприватные заявки в очереди; сортировка по возрасту — в памяти. */
  async function candidates() {
    const snapshot = await db.collection(QUEUE_COLLECTION).where("status", "==", "UNDER_REVIEW").get();
    const rows = (snapshot && Array.isArray(snapshot.docs) ? snapshot.docs : [])
      .filter((doc) => doc && doc.id)
      .slice(0, candidatePage)
      .map((doc) => ({id: doc.id, requestedAtMs: Number(doc.data() && doc.data().requestedAtMs) || 0}));
    rows.sort((a, b) => (a.requestedAtMs !== b.requestedAtMs ? a.requestedAtMs - b.requestedAtMs : (a.id < b.id ? -1 : 1)));
    return rows;
  }

  /** Заявка годна, если её конвейера нет вовсе, или его lease истёк, или он ещё не в терминале. */
  async function claimable(submissionId, nonce) {
    return db.runTransaction(async (tx) => {
      const ref = db.doc(`${PIPELINE_COLLECTION}/${submissionId}`);
      const existing = data(await tx.get(ref));
      const at = now();
      if (existing && existing.lease && existing.lease.expiresAtMs > at) return null;
      if (existing && pipelineRecord(existing).status !== QUEST_STATUS.RUNNING) return null;
      const source = data(await tx.get(db.doc(`${QUEUE_COLLECTION}/${submissionId}`)));
      if (!source || source.status !== "UNDER_REVIEW" || !Array.isArray(source.lessons) ||
        source.lessons.length === 0 || !source.draft || !source.draft.id ||
        !source.draft.catalogId) {
        return null;
      }
      const lessonIds = source.lessons.map((lesson) => lesson && lesson.id).filter(Boolean);
      if (lessonIds.length === 0) return null;
      const catalog = data(await tx.get(db.doc(`catalogs/${source.draft.catalogId}`)));
      const pipeline = createPipelineState({
        submissionId,
        ownerUid: source.ownerUid,
        questId: source.draft.id,
        questTitle: source.draft.title,
        categoryPath: catalog && catalog.name ? String(catalog.name) : "",
        revision: source.localRevision,
        lessonIds,
      });
      pipeline.lease = {ownerNonce: nonce, expiresAtMs: at + leaseMs};
      pipeline.createdAtMs = at;
      pipeline.updatedAtMs = at;
      tx.set(ref, pipeline);
      return {pipeline, source};
    });
  }

  async function resume(submissionId, nonce) {
    return db.runTransaction(async (tx) => {
      const ref = db.doc(`${PIPELINE_COLLECTION}/${submissionId}`);
      const existing = data(await tx.get(ref));
      if (!existing) return null;
      const pipeline = pipelineRecord(existing);
      const at = now();
      if (pipeline.status !== QUEST_STATUS.RUNNING) return null;
      if (pipeline.lease && pipeline.lease.expiresAtMs > at) return null;
      // Чтения до записей: источник заявки читаем, пока транзакция чиста.
      const source = data(await tx.get(db.doc(`${QUEUE_COLLECTION}/${submissionId}`)));
      if (!source) return null;
      pipeline.lease = {ownerNonce: nonce, expiresAtMs: at + leaseMs};
      pipeline.updatedAtMs = at;
      tx.set(ref, pipeline);
      return {pipeline, source};
    });
  }

  /** Веривдикт уже записан (падение после записи, до чекпойнта)? Тогда задание уже сделано. */
  async function verdictExists(lessonId, stage, attempt, submissionId) {
    const snapshot = await db.doc(`${VERDICTS_COLLECTION}/${verdictDocumentId({submissionId, lessonId, stage, attempt})}`).get();
    return Boolean(snapshot && snapshot.exists);
  }

  /** Запись перевода уже стоит (падение после записи, до чекпойнта)? Идемпотентность — имя. */
  async function translationExists(lessonId, attempt, submissionId) {
    const snapshot = await db.doc(`${TRANSLATIONS_COLLECTION}/${translationDocumentId({submissionId, lessonId, attempt})}`).get();
    return Boolean(snapshot && snapshot.exists);
  }

  /**
   * Судьба перевода урока (4.15): переводится только прошедшее. Вердикты этапов уже стоят
   * (nextJob даёт TRANSLATE лишь после DONE проверок); любой FAIL вопрос — SKIPPED без вызова
   * модели. Нечитаемый вердикт — именованная ошибка этапа: молча угадывать исход нельзя.
   */
  async function translateLessonEligibility(pipeline, lessonId, submissionId) {
    const failed = [];
    for (const stage of ["WORDS", "FACTS"]) {
      const stageState = pipeline.lessons[lessonId].stages[stage];
      const verdictId = stageState && stageState.verdictId;
      if (!verdictId) return {ok: false, error: "TRANSLATE_VERDICT_MISSING"};
      const snapshot = await db.doc(`${VERDICTS_COLLECTION}/${verdictId}`).get();
      const verdict = data(snapshot);
      if (!verdict || !Array.isArray(verdict.questionVerdicts)) {
        return {ok: false, error: "TRANSLATE_VERDICT_UNREADABLE"};
      }
      for (const outcome of verdict.questionVerdicts) {
        if (outcome && outcome.verdict === "FAIL") failed.push(outcome.questionId);
      }
    }
    return failed.length > 0 ? {ok: true, skip: true, failedQuestionIds: failed} : {ok: true, skip: false};
  }

  async function writeTranslation({submissionId, pipeline, job, skill, translation}) {
    const record = buildTranslationRecord({
      submissionId,
      lessonId: job.lessonId,
      attempt: job.attempt,
      revision: pipeline.revision,
      model: modelClient.modelName,
      promptVersion: skill.promptVersion,
      detectedLanguage: translation.detectedLanguage,
      questions: translation.questions,
    });
    const translationId = translationDocumentId({submissionId, lessonId: job.lessonId, attempt: job.attempt});
    await db.runTransaction(async (tx) => {
      // Чтение до записей; create как идемпотентный замок — вторая запись того же задания невозможна.
      const current = pipelineRecord(data(await tx.get(db.doc(`${PIPELINE_COLLECTION}/${submissionId}`))));
      applyVerdict(current, job.lessonId, "TRANSLATE", translationId);
      current.updatedAtMs = now();
      tx.create(db.doc(`${TRANSLATIONS_COLLECTION}/${translationId}`), record);
      tx.set(db.doc(`${PIPELINE_COLLECTION}/${submissionId}`), current);
    });
    return translationId;
  }

  async function writeVerdict({submissionId, pipeline, job, skill, verdict}) {
    const record = buildVerdictRecord({
      submissionId,
      lessonId: job.lessonId,
      stage: job.stage,
      attempt: job.attempt,
      revision: pipeline.revision,
      model: modelClient.modelName,
      promptVersion: skill.promptVersion,
      questionVerdicts: verdict.questionVerdicts,
      sources: verdict.sources,
      rationale: verdict.rationale,
    });
    const verdictId = verdictDocumentId({submissionId, lessonId: job.lessonId, stage: job.stage, attempt: job.attempt});
    await db.runTransaction(async (tx) => {
      // Чтение до записей; create в роли идемпотентного замка: повтор задания упадёт ALREADY_EXISTS
      // и вся транзакция откатится — второй вердикт по той же идентичности невозможен.
      const current = pipelineRecord(data(await tx.get(db.doc(`${PIPELINE_COLLECTION}/${submissionId}`))));
      applyVerdict(current, job.lessonId, job.stage, verdictId);
      current.updatedAtMs = now();
      tx.create(db.doc(`${VERDICTS_COLLECTION}/${verdictId}`), record);
      tx.set(db.doc(`${PIPELINE_COLLECTION}/${submissionId}`), current);
    });
    return verdictId;
  }

  async function finishQuest(submissionId, pipeline) {
    pipeline.status = nextJob(pipeline).status;
    pipeline.lease = null;
    pipeline.updatedAtMs = now();
    await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).set(pipeline);
    return pipeline.status;
  }

  /** Один шаг: одно задание (урок × этап × попытка) от запроса модели до записи. */
  async function advanceStep(submissionId, pipeline, source) {
    const job = nextJob(pipeline);
    if (job.done) {
      const status = await finishQuest(submissionId, pipeline);
      return {status, terminal: true};
    }
    const skill = requireStageSkill(stages, job.stage);
    const lesson = (source.lessons || []).find((item) => item && item.id === job.lessonId);
    if (!lesson) {
      applyStageError(pipeline, job.lessonId, job.stage, job.attempt, "REVIEW_LESSON_NOT_IN_SUBMISSION");
      await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).set({...pipeline, updatedAtMs: now()});
      return {status: "ADVANCED", terminal: false};
    }
    // Перевод — свой шаг: пригодность по вердиктам, запись перевода, SKIPPED без вызова.
    if (job.stage === "TRANSLATE") {
      return advanceTranslate(submissionId, pipeline, source, job);
    }
    // Кризисный повтор: вердикт по этому заданию уже стоит — свернуть без повторного вызова.
    if (await verdictExists(job.lessonId, job.stage, job.attempt, submissionId)) {
      const current = pipelineRecord(data(await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).get()));
      applyVerdict(current, job.lessonId, job.stage,
        verdictDocumentId({submissionId, lessonId: job.lessonId, stage: job.stage, attempt: job.attempt}));
      current.updatedAtMs = now();
      await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).set(current);
      return {status: "ADVANCED", terminal: false};
    }

    const lessonContext = {
      questTitle: pipeline.questTitle,
      categoryPath: pipeline.categoryPath,
      lessonId: job.lessonId,
      lessonTitle: lesson.title,
      questions: lessonQuestions(source, job.lessonId),
    };
    const reply = await modelClient.judge({
      system: skill.systemPrompt(),
      user: `${skill.userPrompt(lessonContext)}\n${wrapLessonData(lessonContext)}`,
      // Этап сам называет свои инструменты («Факты» — поиск); транспорт их только доставляет.
      tools: typeof skill.tools === "function" ? skill.tools() : undefined,
    });
    if (!reply.ok) {
      if (reply.code === "REVIEW_MODEL_UNAVAILABLE") {
        // Сбой транспорта — не попытка и не отказ: урок остаётся в очереди, конвейер не двигается.
        return {status: "MODEL_UNAVAILABLE", terminal: false};
      }
      applyStageError(pipeline, job.lessonId, job.stage, job.attempt, reply.code);
      await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).set({...pipeline, updatedAtMs: now()});
      return {status: "ADVANCED", terminal: false};
    }
    const lint = skill.lint(reply.content, lessonContext);
    if (!lint.ok) {
      applyStageError(pipeline, job.lessonId, job.stage, job.attempt, lint.error);
      await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).set({...pipeline, updatedAtMs: now()});
      return {status: "ADVANCED", terminal: false};
    }
    await writeVerdict({submissionId, pipeline, job, skill, verdict: lint.verdict});
    return {status: "ADVANCED", terminal: false};
  }

  /** Шаг перевода (4.15): пригодность по вердиктам → запись → либо SKIPPED без вызова. */
  async function advanceTranslate(submissionId, pipeline, source, job) {
    const eligibility = await translateLessonEligibility(pipeline, job.lessonId, submissionId);
    if (!eligibility.ok) {
      applyStageError(pipeline, job.lessonId, "TRANSLATE", job.attempt, eligibility.error);
      await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).set({...pipeline, updatedAtMs: now()});
      return {status: "ADVANCED", terminal: false};
    }
    if (eligibility.skip) {
      const current = pipelineRecord(data(await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).get()));
      applyStageSkip(current, job.lessonId, "TRANSLATE");
      current.updatedAtMs = now();
      await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).set(current);
      return {status: "ADVANCED", terminal: false};
    }
    // Кризисный повтор: запись перевода уже стоит — свернуть без повторного вызова.
    if (await translationExists(job.lessonId, job.attempt, submissionId)) {
      const current = pipelineRecord(data(await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).get()));
      applyVerdict(current, job.lessonId, "TRANSLATE",
        translationDocumentId({submissionId, lessonId: job.lessonId, attempt: job.attempt}));
      current.updatedAtMs = now();
      await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).set(current);
      return {status: "ADVANCED", terminal: false};
    }
    const skill = requireStageSkill(stages, "TRANSLATE");
    const lesson = (source.lessons || []).find((item) => item && item.id === job.lessonId);
    if (!lesson) {
      applyStageError(pipeline, job.lessonId, "TRANSLATE", job.attempt, "REVIEW_LESSON_NOT_IN_SUBMISSION");
      await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).set({...pipeline, updatedAtMs: now()});
      return {status: "ADVANCED", terminal: false};
    }
    const lessonContext = {
      questTitle: pipeline.questTitle,
      categoryPath: pipeline.categoryPath,
      lessonId: job.lessonId,
      lessonTitle: lesson.title,
      questions: lessonQuestions(source, job.lessonId),
    };
    const reply = await modelClient.judge({
      system: skill.systemPrompt(),
      user: `${skill.userPrompt(lessonContext)}\n${wrapLessonData(lessonContext)}`,
      tools: typeof skill.tools === "function" ? skill.tools() : undefined,
    });
    if (!reply.ok) {
      if (reply.code === "REVIEW_MODEL_UNAVAILABLE") {
        return {status: "MODEL_UNAVAILABLE", terminal: false};
      }
      applyStageError(pipeline, job.lessonId, "TRANSLATE", job.attempt, reply.code);
      await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).set({...pipeline, updatedAtMs: now()});
      return {status: "ADVANCED", terminal: false};
    }
    const lint = skill.lint(reply.content, lessonContext);
    if (!lint.ok) {
      applyStageError(pipeline, job.lessonId, "TRANSLATE", job.attempt, lint.error);
      await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).set({...pipeline, updatedAtMs: now()});
      return {status: "ADVANCED", terminal: false};
    }
    await writeTranslation({submissionId, pipeline, job, skill, translation: lint.translation});
    return {status: "ADVANCED", terminal: false};
  }

  /**
   * Один квест до терминала или до бюджета свипа.
   *
   * Между шагами конвейер перечитывается из хранилища: память шага — не истина, документ —
   * истина (там же виден чужой прогресс после падения).
   */
  async function processQuest(submissionId, claim, budget, nonce) {
    let stageCalls = 0;
    while (stageCalls < budget) {
      const pipeline = pipelineRecord(data(await db.doc(`${PIPELINE_COLLECTION}/${submissionId}`).get()));
      const step = await advanceStep(submissionId, pipeline, claim.source);
      if (step.terminal) return {status: "QUEST_TERMINAL", terminal: step.status};
      if (step.status === "MODEL_UNAVAILABLE") return {status: "MODEL_UNAVAILABLE"};
      stageCalls++;
      if (!await checkpoint(nonce)) return {status: "LEASE_LOST"};
    }
    return {status: "TIME_BUDGET"};
  }

  async function sweep() {
    const startedAtMs = now();
    const nonce = randomBytes(24).toString("hex");
    let acquired;
    try {
      acquired = await acquire(nonce);
    } catch (error) {
      return {status: "STORAGE_FAILED", attempted: 0, stageCalls: 0, startedAtMs};
    }
    if (!acquired.acquired) return {status: "BUSY", attempted: 0, stageCalls: 0, retryAtMs: acquired.retryAtMs};

    let stageCalls = 0;
    let attempted = 0;
    let status = "IDLE";
    try {
      if (STAGE_PLAN.some((stage) => !stages[stage])) {
        // Полуконфигурированный воркер ничего не трогает: очередь ждёт, никакой мусор не пишется.
        report(null, "STAGE_MISSING");
        return {status: "STAGE_MISSING", attempted: 0, stageCalls: 0, startedAtMs};
      }
      for (const candidate of await candidates()) {
        if (stageCalls >= maxStageCallsPerSweep) {
          status = "TIME_BUDGET";
          break;
        }
        if (!owns(stateRecord(data(await stateRef.get())), nonce, now())) {
          status = "LEASE_LOST";
          break;
        }
        let claim = null;
        try {
          claim = (await claimable(candidate.id, nonce)) || (await resume(candidate.id, nonce));
        } catch (error) {
          report(candidate.id, error && error.code ? error.code : "CLAIM_FAILED");
          attempted++;
          continue;
        }
        if (!claim) continue;
        attempted++;
        try {
          const result = await processQuest(candidate.id, claim, maxStageCallsPerSweep - stageCalls, nonce);
          if (result.status === "QUEST_TERMINAL") {
            stageCalls = maxStageCallsPerSweep;
            status = "PROCESSED";
          } else if (result.status === "MODEL_UNAVAILABLE") {
            status = "MODEL_UNAVAILABLE";
            break;
          } else if (result.status === "LEASE_LOST") {
            status = "LEASE_LOST";
            break;
          } else {
            stageCalls = maxStageCallsPerSweep;
            status = "TIME_BUDGET";
            break;
          }
        } catch (error) {
          report(candidate.id, error && error.code ? error.code : "ADVANCE_FAILED");
          status = "PROCESSED_WITH_ERRORS";
          break;
        }
      }
    } catch (error) {
      report(null, "SWEEP_STORAGE_FAILED");
      status = "STORAGE_FAILED";
    }
    let leaseReleased = false;
    try {
      leaseReleased = await release(nonce);
    } catch (error) {
      report(null, "SWEEP_RELEASE_FAILED");
    }
    if (!leaseReleased && status !== "STORAGE_FAILED") status = "LEASE_LOST";
    return {status, attempted, stageCalls, startedAtMs};
  }

  return {sweep};
}

/** Вопросы урока телом заявки: этап получает ровно то, что автор подал. */
function lessonQuestions(source, lessonId) {
  return (source.questions || []).filter((question) => question && question.lessonId === lessonId);
}

module.exports = {
  CANDIDATE_PAGE,
  LEASE_MS,
  MAX_STAGE_CALLS_PER_SWEEP,
  PIPELINE_COLLECTION,
  QUEUE_COLLECTION,
  TRANSLATIONS_COLLECTION,
  VERDICTS_COLLECTION,
  WORKER_STATE_COLLECTION,
  WORKER_STATE_PATH,
  createReviewQueueWorker,
  lessonQuestions,
};
