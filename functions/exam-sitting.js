"use strict";

const {createHash, randomBytes} = require("node:crypto");
const {redact, translateSubmittedAnswer, STATUS: REDACTION_STATUS} = require("./question-redaction");
const {activityPrice, readEconomyConstants, ECONOMY_CONSTANTS_DOC} = require("./economy-constants");
const {maxLifePoints, regenerateLifePoints} = require("./life-points");
const {readPremiumEconomy, planPremiumEconomy, writePremiumEconomy, PREMIUM_CATCHUP_PASSES} = require("./premium-economy");
const {CURRENCY, REASON, LEDGER_PATH, ledgerEntries} = require("./currency-ledger");
const {createExamBank, buildSittingManifest, drawThemeSet, drawFinalSet, poolGate, worthBasisFor, parseQuestionRow} = require("./exam-bank");
const {
  coverageVerdict,
  requiredManifest,
  assessmentStateId,
  coverageDocumentId,
  themePassRegistry,
  confirmedCoverage,
} = require("./exam-coverage");
const {evaluateAdmission, OUTCOME, THEME_COOLDOWN_MS, FINAL_COOLDOWN_MS, worstCoverage} = require("./exam-admission");
const {ANSWER_MS} = require("./exam-scoring");

/**
 * Экзаменационные сидинги — шесть вызовов протокола и одна транзакция старта.
 *
 * Экзамен — собственный жизненный цикл, не обычная попытка, не билет арены и не турнир: свой
 * документ свидетельства, свои вызовы, своя плата. С общими режимами делятся только идентичность,
 * редакция, идемпотентность и скоринг.
 *
 * Пять правил, ради которых файл написан именно так:
 *
 * - **Старт и списание — одна транзакция, идемпотентная и атомарная.** Допуск перепроверяется
 *   внутри той же транзакции, что и списание: прочитанное снаружи не расход. Ключ операции —
 *   детерминированный документ запроса (владелец, epoch аккаунта, operationId, каноническое
 *   тело): повтор возвращает сохранённый результат, чужое тело под тем же ключом — отказ.
 * - **Одна открытая попытка на пару пользователь/оценка.** Старт при открытой восстанавливает её
 *   с исходными дедлайнами, без второго списания и без второго зачёта.
 * - **Первый ограниченный ACK закрепляет дедлайн вопроса.** Дубликат возвращает закреплённое;
 *   ACK никогда не перезапускает и не удлиняет открытые часы. Первый ACK ограничен с момента
 *   создания сидинга; следующие — общим 60-секундным окном от авторитетного закрытия
 *   предыдущего вопроса, замороженным в правилах сидинга.
 * - **Первый ответ побеждает.** Квитанция прочная: владелец, редакция, тело; серверное время
 *   прибытия; одинаковый повтор — та же квитанция, конфликтное повторное использование — отказ
 *   без перезаписи. Восстановимая ошибка обработки квитанцию сохраняет.
 * - **Приватное остаётся приватным.** Канонические содержимые, ключи, выданные перестановки и
 *   цифры живут в документе сидинга; проекция отдаёт только редактированный вопрос текущего
 *   слота, дедлайны и безопасное состояние. Никогда — будущие вопросы, ключи, codeAnswer,
 *   повопросные вердикты.
 *
 * Чистым модуль назвать нельзя: Firestore подводит фабрика, как у арены. Вся логика ветвлений
 * сидит в чистых функциях ниже и проверяется таблицами без стора; транзакционные обёртки
 * проверяются фейковой базой.
 */

const SITTINGS_COLLECTION = "exam_sittings";
const OPEN_SITTINGS_COLLECTION = "exam_open_sittings";
const START_REQUESTS_COLLECTION = "exam_start_requests";
const ANSWER_RECEIPTS_COLLECTION = "exam_answer_receipts";
const COVERAGE_COLLECTION = "exam_coverage";
const ASSESSMENT_STATE_COLLECTION = "exam_assessment_state";
const THEME_GENERATION_COLLECTION = "exam_theme_generation";

/** Версия снимка правил сидинга: таймер, окно, проходной балл, тариф. */
const EXAM_RULES_VERSION = 1;

/**
 * Флаг очереди терминализации: сидинг с ним посетит единственный терминальный писатель.
 * Живёт здесь, рядом с жизненным циклом сидинга, чтобы терминальный worker не создавал
 * циклической зависимости.
 */
const TERMINAL_QUERY_TRUE = "TERMINAL_PENDING";

/** Состояния сидинга — жизненный цикл протокола (Ready→Open→FinishRequested→Closed). */
const SITTING_STATE = Object.freeze({
  OPEN: "OPEN",
  FINISH_REQUESTED: "FINISH_REQUESTED",
  CLOSED: "CLOSED",
});

const SLOT_STATE = Object.freeze({
  PENDING: "PENDING",
  DELIVERED: "DELIVERED",
  ANSWERED: "ANSWERED",
  EXPIRED: "EXPIRED",
});

function fail(code, message = code) {
  const error = new Error(message);
  error.code = code;
  throw error;
}

function canonicalJson(value) {
  if (value === null || typeof value !== "object") return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(",")}]`;
  return `{${Object.keys(value).sort().map((key) =>
    `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(",")}}`;
}

function digest(value) {
  return createHash("sha256").update(canonicalJson(value)).digest("hex");
}

function sittingDocumentId(uid, requestId) {
  return digest(["exam-sitting", uid, requestId]);
}

function openSittingDocumentId(uid, mode, assessmentId) {
  return digest(["exam-open", uid, mode, assessmentId]);
}

function startRequestDocumentId(uid, requestId) {
  return digest(["exam-start", uid, requestId]);
}

function answerReceiptId(sittingId, index) {
  return digest(["exam-answer", sittingId, index]);
}

function data(snapshot) {
  return snapshot && snapshot.exists ? snapshot.data() || null : null;
}

function requireSittingInput(payload) {
  if (!payload || typeof payload !== "object") fail("INVALID_ARGUMENT");
  return payload;
}

function requireOperationIdentity(payload) {
  const requestId = payload && typeof payload.operationId === "string" ? payload.operationId.trim() : "";
  if (!requestId || requestId.includes("/") || requestId.length > 128) {
    fail("INVALID_ARGUMENT", "operationId must be a nonempty document-safe string");
  }
  return requestId;
}

function requireAccountEpoch(payload) {
  const epoch = Math.floor(Number(payload && payload.accountEpoch) || 0);
  if (!Number.isSafeInteger(epoch) || epoch < 0) {
    fail("INVALID_ARGUMENT", "accountEpoch must be a non-negative integer");
  }
  return epoch;
}

/** Канонический хеш тела запроса: тот же ключ с другим телом — не повтор, а конфликт. */
function requestHash(uid, body) {
  return digest({uid, body});
}

/**
 * Публичная проекция текущего вопроса — единственный вид слота, который уезжает клиенту.
 *
 * `publicPayload` — редактированная половина (`SingleChoiceRedacted`, …): правильные варианты
 * отсутствуют, порядок Ordering перемешан сервером и выданная перестановка приватна, mapping
 * FillBlank отсутствует. Будущие слоты не проектируются никогда.
 */
function publicQuestionProjection(sitting, slot, nowMs) {
  return {
    index: slot.index,
    questionId: slot.questionId,
    revision: slot.revision,
    shownDifficulty: slot.shownDifficulty,
    // Сервер замораживает величину; клиентский отсчёт — только отображение.
    answerSeconds: slot.answerSeconds,
    publicPayload: slot.publicPayload,
    ack: slot.ack
      ? {ackedAtMs: slot.ack.ackedAtMs, answerDeadlineMs: slot.ack.answerDeadlineMs}
      : {awaitingAck: true, windowDeadlineMs: slot.windowDeadlineMs},
    serverNowMs: nowMs,
  };
}

/**
 * Безопасная проекция сидинга для ответов вызовов.
 *
 * Никаких цифр, ключей, канонических содержимых, codeAnswer и будущих вопросов. `nextQuestion`
 * присутствует, только когда текущий слот ждёт игрока.
 */
function publicSittingProjection(sitting, nowMs, extra = {}) {
  const current = sitting.slots[sitting.currentIndex];
  const waiting = current && current.state === SLOT_STATE.DELIVERED;
  return {
    schemaVersion: 1,
    sittingId: sitting.sittingId,
    mode: sitting.mode,
    assessmentId: sitting.assessmentId,
    state: sitting.state,
    currentIndex: sitting.currentIndex,
    totalQuestions: sitting.slots.length,
    rules: {
      nextQuestionWindowSeconds: sitting.rules.nextQuestionWindowSeconds,
      firstAckGapSeconds: sitting.rules.firstAckGapSeconds,
      timerRule: "ALL_HARD_ORDINARY",
      timerRuleVersion: sitting.rules.examRulesVersion,
    },
    ...(waiting ? {nextQuestion: publicQuestionProjection(sitting, current, nowMs)} : {}),
    ...extra,
  };
}

/** Восстановительный ответ: тот же сидинг, исходные дедлайны, ни следа второго списания. */
function recoveryProjection(sitting, nowMs) {
  return {
    recovered: true,
    ...publicSittingProjection(sitting, nowMs),
  };
}

/**
 * Слоты сидинга из манифеста банка: приватное свидетельство + публичная половина.
 *
 * Редакция происходит ЗДЕСЬ, при создании сидинга, один раз: ключ и выданная перестановка
 * замораживаются рядом с каноническим содержимым, и скorer позже видит ровно то, что видел
 * игрок, — без второго перемешивания. Survey в наборе не бывает (банк исключает), но редакция
 * обязана подтвердиться: отказ редакции — отказ старта до списания.
 */
function buildSlots(mode, drawn, {random}) {
  const ordered = mode === "ThemeTest" ?
    [...drawn.easy.map((entry) => ({entry, shown: "EASY"})),
      ...drawn.hard.map((entry) => ({entry, shown: "HARD"}))] :
    drawn.hard.map((entry) => ({entry, shown: "HARD"}));
  return ordered.map(({entry, shown}, index) => {
    const payload = JSON.stringify(entry.content);
    const split = redact(payload, entry.difficulty, {questionId: entry.questionId, random});
    if (split.status !== REDACTION_STATUS.REDACTED || !split.key) {
      fail("BANK_UNAVAILABLE", `unredactable question ${entry.questionId}`);
    }
    return {
      index,
      canonicalId: entry.canonicalId,
      questionId: entry.questionId,
      lessonId: entry.lessonId,
      revision: entry.revision,
      difficulty: entry.difficulty,
      shownDifficulty: shown,
      worthSeconds: entry.worthSeconds,
      answerSeconds: entry.answerSeconds,
      // --- Приватное свидетельство: никогда не покидает сервер. ---
      canonicalContent: entry.content,
      key: split.key,
      issuedPermutation: split.key && split.key.order ? split.key.order : null,
      // --- Состояние слота: открыт только нулевой, остальные ждут своей очереди. ---
      state: index === 0 ? SLOT_STATE.DELIVERED : SLOT_STATE.PENDING,
      windowDeadlineMs: null,
      ack: null,
      answer: null,
      answeredAtMs: null,
      publicPayload: split.publicPayload,
    };
  });
}

/**
 * Закрытие просроченного слота и окно следующего.
 *
 * Окно следующего вопроса отсчитывается от АВТОРИТЕТНОГО закрытия предыдущего: приёма
 * своевременного ответа или истечения его исходного дедлайна. Последний слот окна не открывает.
 */
function slotClosureTransition(sitting, index, closedAtMs, {expire = false, answer = null} = {}) {
  const slots = sitting.slots;
  const slot = slots[index];
  const next = {...slot};
  if (answer) {
    next.state = SLOT_STATE.ANSWERED;
    next.answer = answer;
    next.answeredAtMs = closedAtMs;
  } else if (expire) {
    next.state = SLOT_STATE.EXPIRED;
  } else {
    fail("INVALID_ARGUMENT", "closure requires an answer or an expiry");
  }
  const updates = {slots: slots.map((row, at) => (at === index ? next : row))};
  if (index + 1 < slots.length) {
    const following = {...slots[index + 1]};
    if (following.state === SLOT_STATE.PENDING) {
      following.state = SLOT_STATE.DELIVERED;
      following.windowDeadlineMs = closedAtMs + sitting.rules.nextQuestionWindowSeconds * ANSWER_MS;
      updates.slots = updates.slots.map((row, at) => (at === index + 1 ? following : row));
      updates.currentIndex = index + 1;
    }
  } else {
    updates.currentIndex = index;
  }
  return updates;
}

/**
 * Следующий дедлайн сидинга для запроса worker'а.
 *
 * Минимум по всем открытым часам: окно текущего слота, закреплённый дедлайн ответа, первый-ACK
 * разрыв. `null` — часы не идут: сидинг ждёт терминализации по запросу закрытия.
 */
function nextDeadlineAtMs(sitting) {
  let minimum = null;
  const current = sitting.slots[sitting.currentIndex];
  if (current && current.state === SLOT_STATE.DELIVERED) {
    if (current.ack && current.ack.answerDeadlineMs) minimum = current.ack.answerDeadlineMs;
    else if (current.windowDeadlineMs) minimum = current.windowDeadlineMs;
    else if (sitting.firstAckGapDeadlineMs) minimum = sitting.firstAckGapDeadlineMs;
  }
  return minimum;
}

/** Считает ли сидинг себя брошенным: часы ушли, а ответа нет — закрытие по worker'у. */
function abandonmentDeadlineMs(sitting) {
  const deadline = nextDeadlineAtMs(sitting);
  return deadline === null ? null : deadline + sitting.rules.abandonmentGraceMs;
}

/**
 * Чистые ветви ACK. Первый ACK закрепляет дедлайн; дубликат возвращает закреплённое; ACK не
 * перезапускает и не удлинняет открытые часы.
 */
function acknowledgeVerdict(slot, sitting, nowMs) {
  if (!slot || slot.state !== SLOT_STATE.DELIVERED) {
    return {ok: false, code: "SLOT_NOT_OPEN"};
  }
  if (slot.ack) {
    return {ok: true, duplicate: true, ack: slot.ack};
  }
  const bound = slot.index === 0 ? sitting.firstAckGapDeadlineMs : slot.windowDeadlineMs;
  if (!bound || nowMs > bound) {
    return {ok: false, code: "ACK_WINDOW_EXPIRED"};
  }
  const ack = {ackedAtMs: nowMs, answerDeadlineMs: nowMs + slot.answerSeconds * ANSWER_MS};
  // Первый ACK никогда не продлевает час, который уже шёл: дедлайн ответа отсчитывается от
  // подтверждения рендера, но не позже закрытия окна — опоздавший ACK не помогает.
  if (ack.answerDeadlineMs > bound + slot.answerSeconds * ANSWER_MS) {
    ack.answerDeadlineMs = bound + slot.answerSeconds * ANSWER_MS;
  }
  return {ok: true, duplicate: false, ack};
}

/**
 * Чистая ветвь ответа: форма, владелец-редакция-тело, состояние слота.
 *
 * Возвращает вердикт без записи: `accept` — слот можно закрывать этим ответом; `replay` — та же
 * квитанция уже стоит; `conflict` — под той же квитанцией другое тело; `closed` — слот уже
 * закрыт (таймаут зафиксирован до ответа).
 */
function submitVerdict(slot, {revision, fingerprint}) {
  if (!slot) return {kind: "closed", reason: "NO_SLOT"};
  if (slot.state === SLOT_STATE.ANSWERED) {
    return slot.answer && slot.answer.fingerprint === fingerprint ?
      {kind: "replay", answer: slot.answer} : {kind: "conflict"};
  }
  if (slot.state === SLOT_STATE.EXPIRED) return {kind: "closed", reason: "SLOT_EXPIRED"};
  if (slot.state !== SLOT_STATE.DELIVERED) return {kind: "closed", reason: "SLOT_NOT_OPEN"};
  if (!slot.ack) return {kind: "refused", reason: "ACK_REQUIRED"};
  if (Number.isSafeInteger(revision) && revision !== slot.revision) {
    return {kind: "refused", reason: "REVISION_MISMATCH"};
  }
  return {kind: "accept"};
}

/**
 * Фабрика сидингов. Firestore и часы подводятся здесь; вся ветвная логика — чистые функции выше.
 *
 * Премиум-доводка injectable по образцу арены: продакшн подставляет связку readPremiumEconomy /
 * planPremiumEconomy, тесты — свой пойманный-до-дна план. Поведение по умолчанию то же, что у
 * `submitLessonResultEvents`: чекпоинт = повтор той же операции.
 */
function createExamSittings({db, nowMs = () => Date.now(), loadLessonContext, loadExamConfig,
  readRegeneration = defaultReadRegeneration, writeRegeneration = defaultWriteRegeneration}) {
  if (!db || typeof db.runTransaction !== "function" || typeof loadLessonContext !== "function" ||
      typeof loadExamConfig !== "function") {
    throw new TypeError("exam sittings require db, loadLessonContext and loadExamConfig");
  }
  const bank = createExamBank({db, loadLessonContext});

  async function constants(tx) {
    const snapshot = await tx.get(db.doc(ECONOMY_CONSTANTS_DOC));
    return readEconomyConstants(data(snapshot), 0);
  }

  /**
   * Факты допуска внутри транзакции старта: банк, покрытие, реестр тем, кулдаун, плата.
   * Каждое чтение — до первой записи; пересмотр при оптимистичной повторке бесплатен.
   */
  /** Вид прейскуранта по режиму: THEME_TEST / FINAL_EXAM решает сервер, не устройство. */
  function priceKindFor(mode) {
    if (mode === "ThemeTest") return "THEME_TEST";
    if (mode === "FinalExam") return "FINAL_EXAM";
    return fail("INVALID_ARGUMENT", `unknown assessment mode: ${mode}`);
  }

  async function admissionFacts(tx, {uid, mode, assessmentId, config}) {
    const economy = await constants(tx);
    const price = activityPrice(economy, priceKindFor(mode));
    const stateId = assessmentStateId(uid, mode, assessmentId);
    const state = data(await tx.get(db.doc(`${ASSESSMENT_STATE_COLLECTION}/${stateId}`)));
    const openPointer = data(await tx.get(
      db.doc(`${OPEN_SITTINGS_COLLECTION}/${openSittingDocumentId(uid, mode, assessmentId)}`)));
    const openSitting = openPointer ? data(await tx.get(
      db.doc(`${SITTINGS_COLLECTION}/${openPointer.sittingId}`))) : null;

    let coverage = null;
    let certification = null;
    if (mode === "ThemeTest") {
      const themePool = await bank.themePool(tx, assessmentId);
      if (!poolGate("ThemeTest", themePool.pool)) {
        return {early: {kind: OUTCOME.POOL_TOO_SMALL, reason: "DRAW_GATE"}, economy, price, state, openSitting};
      }
      let basis;
      try {
        basis = worthBasisFor("ThemeTest", themePool.pool);
      } catch (_) {
        return {early: {kind: OUTCOME.UNAVAILABLE, reason: "AMBIGUOUS_WORTH_BASIS"}, economy, price, state, openSitting};
      }
      // Манифесты покрытия — из тех же сырых строк, что читал банк: второе чтение вопросов не
      // нужно, и допуск видит ровно тот банк, из которого наберётся сидинг.
      const rowsByLesson = new Map();
      for (const row of themePool.rawRows) {
        if (!rowsByLesson.has(row.lessonId)) rowsByLesson.set(row.lessonId, []);
        rowsByLesson.get(row.lessonId).push(row);
      }
      const verdicts = [];
      for (const lesson of themePool.lessons) {
        const rows = rowsByLesson.get(lesson.lessonId) || [];
        const manifest = requiredManifest(lesson.lessonId, rows);
        const materialization = data(await tx.get(
          db.doc(`${COVERAGE_COLLECTION}/${coverageDocumentId(uid, lesson.lessonId)}`)));
        const confirmed = confirmedCoverage(await readAttemptEvidence(tx, uid, lesson.lessonId));
        verdicts.push(coverageVerdict(manifest, materialization, confirmed).state);
      }
      coverage = worstCoverage(verdicts);
      return {economy, price, state, openSitting, themePool, basis, coverage, certification, bankKnown: true};
    }

    // Финал: курс, сертификация, реестр пройденных тем, поколение коррекций.
    let coursePool;
    try {
      coursePool = await bank.coursePool(tx, assessmentId);
    } catch (error) {
      if (error.code === "COURSE_NOT_FOUND") {
        return {early: {kind: OUTCOME.UNAVAILABLE, reason: "COURSE_NOT_FOUND"}, economy, price, state, openSitting};
      }
      throw error;
    }
    certification = coursePool.certification;
    const themeStates = new Map();
    let pendingGeneration = false;
    for (const themeId of coursePool.themeIds) {
      const themeStateId = assessmentStateId(uid, "ThemeTest", themeId);
      const themeState = data(await tx.get(db.doc(`${ASSESSMENT_STATE_COLLECTION}/${themeStateId}`)));
      themeStates.set(themeId, themeState || {passed: false});
      const generation = data(await tx.get(
        db.doc(`${THEME_GENERATION_COLLECTION}/${themeId}`)));
      if (themeState && Number.isSafeInteger(themeState.passedAtGeneration) &&
          generation && Number.isSafeInteger(generation.generation) &&
          themeState.passedAtGeneration !== generation.generation) {
        pendingGeneration = true;
      }
      if (generation && generation.pendingCorrection === true) pendingGeneration = true;
    }
    const registry = themePassRegistry(themeStates, coursePool.themeIds);
    let basis;
    try {
      basis = worthBasisFor("FinalExam", coursePool.pool);
    } catch (_) {
      return {early: {kind: OUTCOME.UNAVAILABLE, reason: "AMBIGUOUS_WORTH_BASIS"}, economy, price, state, openSitting};
    }
    return {
      economy,
      price,
      state,
      openSitting,
      coursePool,
      basis,
      coverage: {...registry, pendingGeneration},
      certification,
      bankKnown: true,
    };
  }

  /** Свидетельство покрытия из сохранённых обычных событий результата пользователя. */
  async function readAttemptEvidence(tx, uid, lessonId) {
    const snapshot = await tx.get(db.collection(`result_events_public/${digest(uid).slice(0, 2)}/events`)
      .where("lessonId", "==", lessonId).limit(200));
    return snapshot.docs.map((doc) => attemptEvidence(doc.data() || {}));
  }

  /** Свидетельство покрытия из одного сохранённого события обычного прохождения. */
  function attemptEvidence(event) {
    return {
      completed: event.completed === true,
      settlementReceipt: event.settlementReceipt || null,
      served: Array.isArray(event.served) ? event.served : null,
      codeAnswer: typeof event.settledCodeAnswer === "string" ? event.settledCodeAnswer :
        typeof event.codeAnswer === "string" ? event.codeAnswer : null,
    };
  }

  /**
   * Старт: одна транзакция — допуск, списание, сидинг.
   *
   * Возвращает тот же результат при повторе того же operationId, восстановление при открытой
   * попытке, типизированный отказ при закрытом затворе. `RETRY_REGEN` — единственный
   * неветвевой исход: чекпоинт премиума записан, вызов честно просит повтора того же ключа.
   */
  async function start({uid, payload, mode}) {
    const body = requireSittingInput(payload);
    const requestId = requireOperationIdentity(body);
    const accountEpoch = requireAccountEpoch(body);
    const assessmentId = typeof body[mode === "ThemeTest" ? "themeId" : "courseId"] === "string" &&
      body[mode === "ThemeTest" ? "themeId" : "courseId"].trim() ?
      body[mode === "ThemeTest" ? "themeId" : "courseId"].trim() : fail("INVALID_ARGUMENT", "assessmentId is required");
    const shownTerms = body.shownTerms === true;
    const requestPath = `${START_REQUESTS_COLLECTION}/${startRequestDocumentId(uid, requestId)}`;
    const expectedHash = requestHash(uid, {
      mode, assessmentId, accountEpoch, shownTerms, operationId: requestId,
    });
    const entropy = randomBytes(32);
    const rank = (key) => createHash("sha256").update(`${entropy.toString("hex")}:${key}`).digest("hex");

    return runWithRegenRetry(async () => db.runTransaction(async (tx) => {
      const now = Math.floor(nowMs());
      const config = await loadExamConfig(tx);
      // Ключ операции: повтор получает сохранённый результат, конфликтное тело — отказ.
      const existingRequest = data(await tx.get(db.doc(requestPath)));
      if (existingRequest) {
        if (existingRequest.uid !== uid || existingRequest.accountEpoch !== accountEpoch ||
            existingRequest.requestHash !== expectedHash) fail("REQUEST_REUSE");
        return existingRequest.result;
      }

      // Факты допуска — в транзакции, до всякой записи. Баланс доводится здесь же: плата берётся
      // тем, что есть после регенерации, а не тем, что записано.
      const facts = await admissionFacts(tx, {uid, mode, assessmentId, config});
      if (facts.early) return refusalResult({tx, requestPath, uid, requestId, accountEpoch, expectedHash,
        result: refusalPayload(facts.early)});
      const regenerated = await readRegeneratedUser(tx, uid, facts.economy, now);
      if (regenerated.checkpoint) return {regenCheckpoint: true};
      const verdict = evaluateAdmission({
        openSitting: facts.openSitting && facts.openSitting.state !== SITTING_STATE.CLOSED ?
          {state: facts.openSitting.state, sittingId: facts.openSitting.sittingId} : null,
        examEnabled: config.examEnabled === true,
        bankKnown: facts.bankKnown,
        terms: {
          requiredTermsRevision: config.paidTermsRevision,
          acceptedTermsRevision: Number(body.acceptedTermsRevision) || 0,
          shownTerms,
        },
        pool: {drawable: true},
        certification: mode === "FinalExam" ? facts.certification : null,
        coverage: facts.coverage,
        cooldown: {closedAtMs: facts.state ? facts.state.closedAtMs : null},
        cooldownMs: mode === "ThemeTest" ? THEME_COOLDOWN_MS : FINAL_COOLDOWN_MS,
        lifePoints: regenerated.lifePoints,
        price: facts.price,
        nowMs: now,
      });

      if (verdict.kind === OUTCOME.RECOVER) {
        const recovered = facts.openSitting;
        const result = recoveryProjection(recovered, now);
        return commitStartResult({tx, requestPath, uid, requestId, accountEpoch, expectedHash, result});
      }
      if (verdict.kind !== OUTCOME.OK) {
        return refusalResult({tx, requestPath, uid, requestId, accountEpoch, expectedHash,
          result: refusalPayload(verdict)});
      }

      // Набор и заморозка: банк прочитан в этой же транзакции, редакция — здесь же.
      const drawn = mode === "ThemeTest" ?
        drawThemeSet(facts.themePool.pool, rank) : drawFinalSet(facts.coursePool.pool, rank);
      const slots = buildSlots(mode, drawn, {random: undefined});
      const manifest = buildSittingManifest({mode, drawn});
      const sittingId = sittingDocumentId(uid, requestId);
      const sitting = {
        schemaVersion: 1,
        sittingId,
        uid,
        accountEpoch,
        mode,
        assessmentId,
        state: SITTING_STATE.OPEN,
        createdAtMs: now,
        closedAtMs: null,
        closeReason: null,
        currentIndex: 0,
        version: 1,
        rules: {
          examRulesVersion: EXAM_RULES_VERSION,
          passMarkPercent: config.passMarkPercent,
          nextQuestionWindowSeconds: config.nextQuestionWindowSeconds,
          firstAckGapSeconds: config.nextQuestionWindowSeconds,
          abandonmentGraceMs: config.abandonmentGraceMs,
          paidTermsRevision: config.paidTermsRevision,
          manifestHash: manifest.manifestHash,
        },
        // Заморозка базы выплаты C1: средние по пулу на момент старта. Жребий и поздние правки
        // содержимого цену попытки не двигают.
        bank: facts.basis,
        certification: mode === "FinalExam" ? facts.certification : null,
        courseVersion: mode === "FinalExam" && facts.coursePool ?
          Number(facts.coursePool.quest.version) || null : null,
        slots,
        firstAckGapDeadlineMs: now + config.nextQuestionWindowSeconds * ANSWER_MS,
        terminal: null,
        terminalQueryFlag: TERMINAL_QUERY_TRUE,
        nextDeadlineAtMs: now + config.nextQuestionWindowSeconds * ANSWER_MS,
      };
      const price = facts.price;
      tx.create(db.doc(`${SITTINGS_COLLECTION}/${sittingId}`), sitting);
      tx.set(db.doc(`${OPEN_SITTINGS_COLLECTION}/${openSittingDocumentId(uid, mode, assessmentId)}`), {
        uid, mode, assessmentId, sittingId, state: SITTING_STATE.OPEN, openedAtMs: now,
      });
      // userPatch после баланса НЕ идёт: прем-патч не должен затирать только что списанные
      // очки. Порядок полей здесь — часть контракта списания.
      tx.set(db.doc(`users/${uid}`), {
        ...regenerated.plan.userPatch,
        lifePoints: regenerated.lifePoints - price,
        lifePointsUpdatedAtMs: regenerated.lifeUpdatedAtMs,
        updatedAtMs: now,
      }, {merge: true});
      writeRegeneration(tx, {uid, plan: regenerated.plan});
      for (const entry of ledgerEntries({
        uid, reason: REASON.ATTEMPT_TOLL, sourceId: sittingId, atMs: now,
        deltas: {[CURRENCY.STANDARD_CHARGE_POINTS]: -price},
        details: {mode, assessmentId, kind: "EXAM_ENTRY"},
      })) {
        tx.create(db.doc(`${LEDGER_PATH}/${entry.id}`), entry);
      }
      const result = {recovered: false, debited: price,
        lifePoints: regenerated.lifePoints - price, ...publicSittingProjection(sitting, now)};
      return commitStartResult({tx, requestPath, uid, requestId, accountEpoch, expectedHash, result});
    }), {mode, assessmentId});
  }

  function refusalPayload(verdict) {
    return {refused: true, kind: verdict.kind, ...(verdict.availableAtMs ? {availableAtMs: verdict.availableAtMs} : {}),
      ...(verdict.reason ? {reason: verdict.reason} : {})};
  }

  function commitStartResult({tx, requestPath, uid, requestId, accountEpoch, expectedHash, result}) {
    tx.create(db.doc(requestPath), {
      uid, requestId, accountEpoch, requestHash: expectedHash, result,
      state: "completed", createdAtMs: Math.floor(nowMs()),
    });
    return result;
  }

  function refusalResult({tx, requestPath, uid, requestId, accountEpoch, expectedHash, result}) {
    return commitStartResult({tx, requestPath, uid, requestId, accountEpoch, expectedHash, result});
  }

  /**
   * Премиум-доводка вокруг транзакции старта: чекпоинт пишется отдельной короткой транзакцией,
   * затем старт повторяет тот же ключ операции заново (arena `RETRY_REGEN` идиома).
   */
  async function runWithRegenRetry(action, context) {
    for (let pass = 0; pass < PREMIUM_CATCHUP_PASSES; pass += 1) {
      const result = await action();
      if (!result || result.regenCheckpoint !== true) return result;
    }
    fail("REGEN_RETRY", "premium history is catching up; retry the same operation");
  }

  /**
   * Чтение пользователя с доводкой регенерации. Внутри транзакции старта: возвращает
   * доведённый баланс и (при необходимости чекпоинта) маркер повторной попытки.
   */
  async function readRegeneratedUser(tx, uid, economy, now) {
    const userRef = db.doc(`users/${uid}`);
    const user = data(await tx.get(userRef));
    if (!user) fail("USER_NOT_FOUND");
    const plan = await readRegeneration(tx, {uid, user, economy, now});
    if (!plan.caughtUp) {
      writeRegeneration(tx, {uid, plan});
      tx.set(userRef, plan.userPatch, {merge: true});
      return {checkpoint: true};
    }
    const ownedSlots = Math.min(Number(user.standardHearts) || 0, economy.standard.maxOwned);
    const ceiling = maxLifePoints(ownedSlots);
    const regenMs = economy.standard.regenMs /
      (plan.userPatch.hasPremium ? economy.standard.premiumRegenDivisor : 1);
    const regenerated = regenerateLifePoints(
      Number(plan.userPatch.lifePoints) || 0,
      Number(plan.userPatch.lifePointsUpdatedAtMs) || now,
      now, ceiling, regenMs,
    );
    return {checkpoint: false, user, plan, lifePoints: regenerated.points,
      lifeUpdatedAtMs: regenerated.updatedAtMs};
  }

  /** Загрузка сидинга с владением и формой. */
  async function loadOwnedSitting(tx, uid, sittingId) {
    const sitting = data(await tx.get(db.doc(`${SITTINGS_COLLECTION}/${sittingId}`)));
    if (!sitting) fail("NOT_FOUND", "sitting not found");
    if (sitting.uid !== uid) fail("PERMISSION_DENIED", "sitting belongs to another account");
    return sitting;
  }

  /**
   * acknowledgeQuestion: первый ограниченный ACK закрепляет дедлайн вопроса; дубликат возвращает
   * закреплённое; опоздавший ACK не перезапускает и не удлиняет часы.
   */
  async function acknowledgeQuestion({uid, payload}) {
    const body = requireSittingInput(payload);
    const requestId = requireOperationIdentity(body);
    const sittingId = typeof body.sittingId === "string" ? body.sittingId : fail("INVALID_ARGUMENT", "sittingId is required");
    const index = Math.floor(Number(body.questionIndex));
    if (!Number.isSafeInteger(index) || index < 0) fail("INVALID_ARGUMENT", "questionIndex is required");
    return db.runTransaction(async (tx) => {
      const now = Math.floor(nowMs());
      const sitting = await loadOwnedSitting(tx, uid, sittingId);
      if (sitting.state === SITTING_STATE.CLOSED) return {state: SITTING_STATE.CLOSED, sittingId};
      const slot = sitting.slots[index];
      const verdict = acknowledgeVerdict(slot, sitting, now);
      if (!verdict.ok) {
        return {acknowledged: false, reason: verdict.code,
          ...(slot && slot.ack ? {ack: slot.ack} : {})};
      }
      if (verdict.duplicate) {
        return {acknowledged: true, duplicate: true, ack: verdict.ack,
          ...publicQuestionProjection(sitting, slot, now)};
      }
      const slots = sitting.slots.map((row, at) => (at === index ?
        {...row, ack: verdict.ack, state: SLOT_STATE.DELIVERED} : row));
      tx.set(db.doc(`${SITTINGS_COLLECTION}/${sittingId}`), {
        slots, version: sitting.version + 1,
        nextDeadlineAtMs: slots[index].ack.answerDeadlineMs,
      }, {merge: true});
      return {acknowledged: true, duplicate: false, ack: verdict.ack,
        ...publicQuestionProjection({...sitting, slots}, slots[index], now)};
    });
  }

  /**
   * submitAnswer: первый ответ побеждает. Квитанция читается до записи; одинаковый повтор
   * возвращает ту же квитанцию; конфликтное тело под той же квитанцией — отказ; закрытый слот
   * не принимает ответ (таймаут уже зафиксирован).
   */
  async function submitAnswer({uid, payload}) {
    const body = requireSittingInput(payload);
    const sittingId = typeof body.sittingId === "string" ? body.sittingId : fail("INVALID_ARGUMENT", "sittingId is required");
    const index = Math.floor(Number(body.questionIndex));
    if (!Number.isSafeInteger(index) || index < 0) fail("INVALID_ARGUMENT", "questionIndex is required");
    const revision = body.revision === undefined ? undefined : Math.floor(Number(body.revision));
    if (!body.answer || typeof body.answer !== "object" || Array.isArray(body.answer)) {
      fail("INVALID_ARGUMENT", "answer payload is required");
    }
    return db.runTransaction(async (tx) => {
      const now = Math.floor(nowMs());
      const sitting = await loadOwnedSitting(tx, uid, sittingId);
      if (sitting.state === SITTING_STATE.CLOSED) {
        return {state: SITTING_STATE.CLOSED, sittingId, accepted: false, reason: "SITTING_CLOSED"};
      }
      const slot = sitting.slots[index];
      const answer = {payload: body.answer};
      const fingerprint = digest({sittingId, index, revision: revision === undefined ? null : revision, answer});
      const verdict = submitVerdict(slot, {revision, fingerprint});
      if (verdict.kind === "replay") {
        return {accepted: true, duplicate: true, receipt: verdict.answer.receipt,
          ...sittingResponseAfter(sitting, now)};
      }
      if (verdict.kind === "conflict") fail("ANSWER_CONFLICT", "answer identity is already used by another payload");
      if (verdict.kind === "closed") {
        return {accepted: false, reason: verdict.reason, ...sittingResponseAfter(sitting, now)};
      }
      if (verdict.kind === "refused") {
        return {accepted: false, reason: verdict.reason, ...sittingResponseAfter(sitting, now)};
      }
      // Своевременность: дедлайн ответа закреплён ACK'ом. Ответ после дедлайна, но до закрытия
      // worker'ом, принимается по серверному времени прибытия — «своевременный» решает запись.
      const receiptId = answerReceiptId(sittingId, index);
      const receipt = {
        receiptId, sittingId, index, revision: slot.revision,
        uid, fingerprint, receivedAtMs: now,
      };
      const receiptRef = db.doc(`${ANSWER_RECEIPTS_COLLECTION}/${receiptId}`);
      const existingReceipt = data(await tx.get(receiptRef));
      if (existingReceipt) {
        if (existingReceipt.fingerprint !== fingerprint) {
          fail("ANSWER_CONFLICT", "answer identity is already used by another payload");
        }
        return {accepted: true, duplicate: true, receipt: existingReceipt,
          ...sittingResponseAfter(sitting, now)};
      }
      const canonicalAnswer = translateSubmittedAnswer(body.answer, slot.key);
      if (!canonicalAnswer) fail("INVALID_ARGUMENT", "answer shape does not fit the question");
      const answerRecord = {
        fingerprint,
        receipt,
        // Оба вида ответа — приватное свидетельство: выданная перестановка и канонический ответ.
        issuedAnswer: body.answer,
        canonicalAnswer,
      };
      const updates = slotClosureTransition(sitting, index, now, {answer: answerRecord});
      const updatedSitting = {...sitting, ...updates,
        state: index + 1 >= sitting.slots.length ? SITTING_STATE.FINISH_REQUESTED : sitting.state};
      tx.create(receiptRef, receipt);
      tx.set(db.doc(`${SITTINGS_COLLECTION}/${sittingId}`), {
        ...updates,
        state: updatedSitting.state,
        version: sitting.version + 1,
        finishRequestedAtMs: updatedSitting.state === SITTING_STATE.FINISH_REQUESTED ?
          now : sitting.finishRequestedAtMs || null,
        nextDeadlineAtMs: nextDeadlineAtMs(updatedSitting),
      }, {merge: true});
      return {accepted: true, duplicate: false, receipt,
        ...sittingResponseAfter(updatedSitting, now)};
    });
  }

  /** Безопасное состояние после операции: следующий вопрос или ожидание терминализации. */
  function sittingResponseAfter(sitting, now) {
    return publicSittingProjection(sitting, now);
  }

  /**
   * finishSitting: запрос закрытия или уже-закрытое состояние. Передние вызовы никогда не
   * становятся вторым терминальным писателем: закрытие фиксирует worker.
   */
  async function finishSitting({uid, payload}) {
    const body = requireSittingInput(payload);
    const sittingId = typeof body.sittingId === "string" ? body.sittingId : fail("INVALID_ARGUMENT", "sittingId is required");
    return db.runTransaction(async (tx) => {
      const now = Math.floor(nowMs());
      const sitting = await loadOwnedSitting(tx, uid, sittingId);
      if (sitting.state === SITTING_STATE.CLOSED) {
        return {sittingId, state: SITTING_STATE.CLOSED, alreadyClosed: true};
      }
      if (sitting.state === SITTING_STATE.FINISH_REQUESTED) {
        return {sittingId, state: SITTING_STATE.FINISH_REQUESTED, alreadyRequested: true};
      }
      tx.set(db.doc(`${SITTINGS_COLLECTION}/${sittingId}`), {
        state: SITTING_STATE.FINISH_REQUESTED,
        finishRequestedAtMs: now,
        version: sitting.version + 1,
        nextDeadlineAtMs: null,
      }, {merge: true});
      return {sittingId, state: SITTING_STATE.FINISH_REQUESTED, alreadyRequested: false};
    });
  }

  /**
   * fetchSittingResult: явное открытое состояние или авторитетный терминальный результат.
   * Чтение не терминализирует: до закрытия worker'ом результат не существует.
   */
  async function fetchSittingResult({uid, payload}) {
    const body = requireSittingInput(payload);
    const sittingId = typeof body.sittingId === "string" ? body.sittingId : fail("INVALID_ARGUMENT", "sittingId is required");
    return db.runTransaction(async (tx) => {
      const now = Math.floor(nowMs());
      const sitting = await loadOwnedSitting(tx, uid, sittingId);
      if (sitting.state !== SITTING_STATE.CLOSED || !sitting.terminal) {
        return {sittingId, state: sitting.state, result: null, open: true};
      }
      return {sittingId, state: SITTING_STATE.CLOSED, open: false, result: sitting.terminal.projection};
    });
  }

  return {
    start,
    acknowledgeQuestion,
    submitAnswer,
    finishSitting,
    fetchSittingResult,
    // Внутренние assistants для worker'а и тестов: чистые ветви и id-функции.
    internals: {
      acknowledgeVerdict,
      answerReceiptId,
      abandonmentDeadlineMs,
      attemptEvidence,
      buildSlots,
      coverageDocumentId,
      nextDeadlineAtMs,
      openSittingDocumentId,
      publicQuestionProjection,
      publicSittingProjection,
      recoveryProjection,
      sittingDocumentId,
      slotClosureTransition,
      startRequestDocumentId,
      submitVerdict,
      SLOT_STATE,
      SITTING_STATE,
    },
  };
}

/** Пойманная премиум-доводка по умолчанию — та же связка, что у приёмника прохождений. */
async function defaultReadRegeneration(tx, {uid, user, economy, now}) {
  const snapshot = await readPremiumEconomy({db, transaction: tx, uid, operation: {kind: "ADVANCE"}});
  return planPremiumEconomy({snapshot, userData: user, constants: economy, now, operation: {kind: "ADVANCE"}});
}

function defaultWriteRegeneration(tx, {uid, plan}) {
  writePremiumEconomy({db, transaction: tx, uid, plan});
}

module.exports = {
  ANSWER_RECEIPTS_COLLECTION,
  ASSESSMENT_STATE_COLLECTION,
  COVERAGE_COLLECTION,
  EXAM_RULES_VERSION,
  OPEN_SITTINGS_COLLECTION,
  SITTINGS_COLLECTION,
  SLOT_STATE,
  SITTING_STATE,
  START_REQUESTS_COLLECTION,
  TERMINAL_QUERY_TRUE,
  THEME_GENERATION_COLLECTION,
  acknowledgeVerdict,
  createExamSittings,
  nextDeadlineAtMs,
  openSittingDocumentId,
  publicQuestionProjection,
  publicSittingProjection,
  publicQuestionProjectionSafe: publicQuestionProjection,
  sittingDocumentId,
  slotClosureTransition,
  startRequestDocumentId,
  submitVerdict,
};
