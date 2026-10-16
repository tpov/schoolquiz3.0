"use strict";

const {randomBytes, createHash} = require("node:crypto");
const {scoreSlot, sittingPercent, themeStarsTenths, passedAgainst, safeAdvice,
  resultProjection, ANSWER_MS, SITTING_SIZE} = require("./exam-scoring");
const {assessmentReward} = require("./exam-reward");
const {THEME_COOLDOWN_MS, FINAL_COOLDOWN_MS} = require("./exam-admission");
const {assessmentStateId} = require("./exam-coverage");
const {SITTING_STATE, SLOT_STATE, openSittingDocumentId, TERMINAL_QUERY_TRUE} = require("./exam-sitting");
const {issuanceFact, signingWorkRecord} = require("./exam-certificates");
const {CURRENCY, REASON, LEDGER_PATH, ledgerEntries} = require("./currency-ledger");

/**
 * Единственный терминальный писатель экзамена — ограниченный worker по образцу арены.
 *
 * Передние вызовы двигают сидинг, просят закрытия или читают; терминализирует ТОЛЬКО worker:
 * один писатель, CAS по версии сидинга, аренда с fencing-nonce на цикл обхода, ограниченные
 * шаги, изоляция ядовитых записей. Задержка планировщика никогда не продлевает дедлайны
 * вопросов: всё, что просрочено, закрывается неотвеченным, и цепочка окон продолжается.
 *
 * Терминальная транзакция — один коммит: результат, проекция кулдауна, денежные эффекты; для
 * успешного финала — ещё и неизменяемый факт выпуска с прочной работой подписывания. Откат
 * не оставляет ни успеха, ни награды, ни выпуска.
 *
 * onSchedule-адаптер добавляется отдельной обёрткой в index.js и НИЧЕГО не активирует:
 * деплой, расписание и окружение — решение владельца (Q-EX-5).
 */

const WORKER_STATE_COLLECTION = "exam_sitting_worker_state";
const WORKER_STATE_PATH = `${WORKER_STATE_COLLECTION}/sweep`;
const SITTINGS_COLLECTION = "exam_sittings";
const OPEN_SITTINGS_COLLECTION = "exam_open_sittings";
const ASSESSMENT_STATE_COLLECTION = "exam_assessment_state";
const REWARD_RECEIPTS_COLLECTION = "exam_reward_receipts";
const FIRST_PASS_AWARDS_COLLECTION = "exam_first_pass_awards";
const THEME_GENERATION_COLLECTION = "exam_theme_generation";
const COVERAGE_COLLECTION = "exam_coverage";

const LEASE_MS = 660_000;
const FUNCTION_TIMEOUT_MS = 540_000;
const MAX_SITTINGS_PER_SWEEP = 10;
const MAX_ADVANCES_PER_SITTING = 24;

const CLOSE_REASON = Object.freeze({
  COMPLETED: "COMPLETED",
  TIMEOUT: "TIMEOUT",
  ABANDONED: "ABANDONED",
});

const SAFE_ERROR_CODES = new Set(["UNAVAILABLE", "INVALID_ARGUMENT", "NOT_FOUND", "INCONSISTENT_TERMINAL"]);

function fail(code, message = code) {
  const error = new Error(message);
  error.code = code;
  throw error;
}

function data(snapshot) {
  return snapshot && snapshot.exists ? snapshot.data() || null : null;
}

/**
 * Цифра слота для агрегата.
 *
 * Отвеченный слот считается общим скорером по каноническому содержимому и каноническому ответу;
 * неотвеченный, просроченный и не подтверждённый ACK — цифра 1: таймаут не оплаченный пропуск и
 * не верный ответ, остальные заработанные награды он не меняет.
 */
function slotDigit(slot) {
  if (slot && slot.state === SLOT_STATE.ANSWERED && slot.answer) {
    return scoreSlot(slot.canonicalContent, slot.answer.canonicalAnswer);
  }
  return 1;
}

/** Цифры всех слотов терминализируемого сидинга. */
function sittingDigits(sitting) {
  const digits = [];
  for (const slot of sitting.slots) digits.push(slotDigit(slot));
  return digits;
}

/**
 * План одного ограниченного шага — чистая функция.
 *
 * - `TERMINALIZE` — сидинг просил закрытия или все слоты закрыты: терминальная транзакция.
 * - `ADVANCE` — есть просроченный открытый слот: закрыть неотвеченным, открыть окно следующего
 *   (цепочка); записи возвращаются готовыми.
 * - `IDLE` — часы идут: делать нечего, задержка планировщика дедлайны не двигает.
 */
function advancePlan(sitting, nowMs) {
  if (sitting.state === SITTING_STATE.CLOSED) return {kind: "IDLE"};
  if (sitting.state === SITTING_STATE.FINISH_REQUESTED) {
    return {kind: "TERMINALIZE", closeReason: CLOSE_REASON.COMPLETED};
  }
  const current = sitting.slots[sitting.currentIndex];
  if (current && current.state === SLOT_STATE.DELIVERED) {
    const bound = current.ack ? current.ack.answerDeadlineMs :
      current.index === 0 ? sitting.firstAckGapDeadlineMs : current.windowDeadlineMs;
    if (bound && nowMs > bound) {
      const updates = advanceExpiredSlot(sitting, current.index, bound, nowMs);
      const last = current.index + 1 >= sitting.slots.length;
      return {
        kind: "ADVANCE",
        updates: {
          ...updates,
          state: last ? SITTING_STATE.FINISH_REQUESTED : sitting.state,
          finishRequestedAtMs: last ? nowMs : sitting.finishRequestedAtMs || null,
          closeReasonCandidate: last ? CLOSE_REASON.TIMEOUT : null,
        },
      };
    }
    return {kind: "IDLE"};
  }
  // Текущий слот не открыт: всё отвечено/закрыто, а сидинг всё ещё OPEN — на случай
  // прерванной записи: закрыть как брошенный (ответа давно нет), остаток оценён нормально.
  const openSlots = sitting.slots.filter((slot) => slot.state === SLOT_STATE.DELIVERED);
  if (openSlots.length === 0) {
    return {kind: "TERMINALIZE", closeReason: CLOSE_REASON.ABANDONED};
  }
  return {kind: "IDLE"};
}

/**
 * Закрытие просроченного слота неотвеченным и окно следующего.
 *
 * Якорь окна — ИСХОДНЫЙ дедлайн закрытого вопроса (не «сейчас» worker'а): задержка планировщика
 * не сокращает и не продлевает окно следующего вопроса.
 */
function advanceExpiredSlot(sitting, index, deadlineMs, nowMs) {
  const slots = sitting.slots.map((slot, at) => (at === index ?
    {...slot, state: SLOT_STATE.EXPIRED, windowDeadlineMs: null} : slot));
  const updates = {slots};
  if (index + 1 < slots.length) {
    const following = {...slots[index + 1]};
    if (following.state === SLOT_STATE.PENDING) {
      following.state = SLOT_STATE.DELIVERED;
      following.windowDeadlineMs = deadlineMs + sitting.rules.nextQuestionWindowSeconds * ANSWER_MS;
      updates.slots = updates.slots.map((slot, at) => (at === index + 1 ? following : slot));
      updates.currentIndex = index + 1;
    }
  }
  updates.nextDeadlineAtMs = null;
  return updates;
}

/**
 * Терминальный расчёт — чистая функция.
 *
 * Агрегат по всем двадцати слотам (неотвеченные — 1), процент общим скорером, проход против
 * захваченного балла, линейные звёзды темы, безопасный совет фиксированной длины.
 */
function terminalOutcome(sitting, {closedAtMs, closeReason, resultVersion}) {
  const digits = sittingDigits(sitting);
  if (digits.length !== SITTING_SIZE) fail("INCONSISTENT_TERMINAL", "sitting size drift");
  const percent = sittingPercent(digits);
  const passMark = sitting.rules.passMarkPercent;
  const passed = passedAgainst(percent, passMark);
  const starsTenths = sitting.mode === "ThemeTest" ? themeStarsTenths(percent) : null;
  const advice = safeAdvice({percent, passed, mode: sitting.mode});
  const projection = resultProjection({
    sittingId: sitting.sittingId,
    mode: sitting.mode,
    assessmentId: sitting.assessmentId,
    resultVersion,
    percent,
    passed,
    starsTenths,
    advice,
    closedAtMs,
    nextAvailableAtMs: closedAtMs + (sitting.mode === "ThemeTest" ? THEME_COOLDOWN_MS : FINAL_COOLDOWN_MS),
  });
  return {digits, percent, passed, starsTenths, advice, projection, closeReason, resultVersion};
}

/**
 * План наград C1 — чистая функция.
 *
 * Тема: только первый проход, b=0 до награды; неоплаченный провал темы учтённый максимум не
 * двигает. Финал: каждый терминальный сидинг, включая провал; b = max(b, p) даже при нулевой
 * после округления выплате. Испорченная заморозка базы — явная ошибка, не ноль.
 */
function planReward({sitting, percent, passed, assessmentState, hasFirstPassAward}) {
  const state = assessmentState || {};
  const b = Math.max(0, Math.floor(Number(state.maxAccountedPercent) || 0));
  if (sitting.mode === "ThemeTest") {
    if (passed !== true || hasFirstPassAward === true) {
      return {pays: false, bNext: b, reason: passed ? "FIRST_PASS_ALREADY_AWARDED" : "THEME_NOT_PASSED"};
    }
    const reward = assessmentReward({mode: "ThemeTest", percent, accountedPercent: 0, basis: sitting.bank});
    return {pays: true, firstPass: true, ...reward};
  }
  const reward = assessmentReward({mode: "FinalExam", percent, accountedPercent: b, basis: sitting.bank});
  return {pays: true, firstPass: false, ...reward};
}

/**
 * Фабрика терминального писателя. `loadExamConfig(tx)` — тот же конфиг, что у старта
 * (проходной балл, окна, ключ подписи сертификатов — идентичность, не секрет).
 */
function createExamTerminal({db, nowMs = () => Date.now(), loadExamConfig, log = () => {}}) {
  if (!db || typeof db.runTransaction !== "function" || typeof loadExamConfig !== "function") {
    throw new TypeError("exam terminal requires db and loadExamConfig");
  }
  const now = () => Math.floor(nowMs());

  /**
   * Один терминальный шаг над сидингом.
   *
   * CAS по версии сидинга: конкурентный шаг или поздний ACK/ответ, уже перезаписавшие документ,
   * дают несовпадение версии и честный отказ шага — тот же сидинг просто посетят снова.
   */
  async function advanceSitting({sittingId}) {
    return db.runTransaction(async (tx) => {
      const at = now();
      const sittingRef = db.doc(`${SITTINGS_COLLECTION}/${sittingId}`);
      const sitting = data(await tx.get(sittingRef));
      if (!sitting) return {sittingId, status: "NOT_FOUND"};
      if (sitting.state === SITTING_STATE.CLOSED) return {sittingId, status: "CLOSED"};
      const plan = advancePlan(sitting, at);
      if (plan.kind === "IDLE") return {sittingId, status: "IDLE"};

      if (plan.kind === "ADVANCE") {
        tx.set(sittingRef, {...plan.updates, version: sitting.version + 1,
          nextDeadlineAtMs: nextDeadlineOf({...sitting, ...plan.updates})}, {merge: true});
        return {sittingId, status: "ADVANCED"};
      }

      // --- Терминализация: один коммит — результат, кулдаун, деньги, выпуск. ---
      const config = await loadExamConfig(tx);
      const closedAtMs = at;
      const resultVersion = 1;
      // Последний вопрос, закрытый таймаутом цепочки, просит причину TIMEOUT: кандидат
      // вычислен в плане шага и обязан доехать до свидетельства закрытия.
      const closeReason = plan.closeReasonCandidate || plan.closeReason;
      const outcome = terminalOutcome(sitting, {closedAtMs, closeReason, resultVersion});
      const stateId = assessmentStateId(sitting.uid, sitting.mode, sitting.assessmentId);
      const stateRef = db.doc(`${ASSESSMENT_STATE_COLLECTION}/${stateId}`);
      const state = data(await tx.get(stateRef)) || {};
      const firstPassRef = db.doc(`${FIRST_PASS_AWARDS_COLLECTION}/${stateId}`);
      const hasFirstPass = sitting.mode === "ThemeTest" ? !!(data(await tx.get(firstPassRef))) : false;

      let rewardPlan;
      try {
        rewardPlan = planReward({sitting, percent: outcome.percent, passed: outcome.passed,
          assessmentState: state, hasFirstPassAward: hasFirstPass});
      } catch (error) {
        // Испорченная или отсутствующая финансовая база — явная незакрытая работа: сидинг не
        // получает фальшивого результата, оператор получает запись, сидинг остаётся в очереди.
        report(sittingId, "INVALID_REWARD_BASIS");
        tx.set(sittingRef, {terminalError: "INVALID_REWARD_BASIS", version: sitting.version + 1}, {merge: true});
        return {sittingId, status: "POISON"};
      }

      const terminal = {
        receiptId: `exam-terminal:${sittingId}`,
        resultVersion,
        closedAtMs,
        closeReason,
        projection: outcome.projection,
      };
      tx.set(sittingRef, {
        state: SITTING_STATE.CLOSED,
        closedAtMs,
        closeReason,
        terminal,
        version: sitting.version + 1,
        nextDeadlineAtMs: null,
      }, {merge: true});
      tx.set(db.doc(`${OPEN_SITTINGS_COLLECTION}/${openSittingDocumentId(
        sitting.uid, sitting.mode, sitting.assessmentId)}`), {
        state: SITTING_STATE.CLOSED, closedAtMs,
      }, {merge: true});

      // Проекция кулдауна и лучший результат: монотонно, одним коммитом с результатом.
      const cooldownUntilMs = closedAtMs + (sitting.mode === "ThemeTest" ? THEME_COOLDOWN_MS : FINAL_COOLDOWN_MS);
      const nextGeneration = sitting.mode === "ThemeTest" ?
        await advanceThemeGeneration(tx, sitting.assessmentId, closedAtMs) : null;
      const nextState = {
        uid: sitting.uid,
        mode: sitting.mode,
        assessmentId: sitting.assessmentId,
        closedAtMs,
        cooldownUntilMs,
        bestPercent: Math.max(Math.floor(Number(state.bestPercent) || 0), outcome.percent),
        passed: state.passed === true || outcome.passed === true,
        maxAccountedPercent: rewardPlan.pays ? rewardPlan.bNext :
          Math.max(0, Math.floor(Number(state.maxAccountedPercent) || 0)),
        lastResultVersion: resultVersion,
        lastSittingId: sitting.sittingId,
        ...(sitting.mode === "ThemeTest" && outcome.passed ?
          {passedAtGeneration: nextGeneration} : {}),
        updatedAtMs: closedAtMs,
      };
      tx.set(stateRef, nextState, {merge: true});

      if (rewardPlan.pays) {
        const accountedBefore = sitting.mode === "FinalExam" ?
          Math.max(0, Math.floor(Number(state.maxAccountedPercent) || 0)) : 0;
        const receipt = {
          receiptId: `exam-reward:${sitting.sittingId}`,
          sittingId: sitting.sittingId,
          uid: sitting.uid,
          mode: sitting.mode,
          assessmentId: sitting.assessmentId,
          resultVersion,
          p: outcome.percent,
          accountedPercentBefore: accountedBefore,
          accountedPercentAfter: rewardPlan.bNext,
          nolics: rewardPlan.nolics,
          skillPoints: rewardPlan.skillPoints,
          ruleVersion: rewardPlan.ruleVersion,
          firstPass: rewardPlan.firstPass === true,
          unroundedN: rewardPlan.unroundedN,
          closedAtMs,
        };
        tx.create(db.doc(`${REWARD_RECEIPTS_COLLECTION}/${sitting.sittingId}`), receipt);
        if (rewardPlan.firstPass) {
          // Один чек первого прохода на пару пользователь/тема: повтор и коррекция больше не
          // платят. `awardedAt` рядом — требование протокола.
          tx.create(firstPassRef, {
            uid: sitting.uid, mode: sitting.mode, assessmentId: sitting.assessmentId,
            awardedAtMs: closedAtMs, sittingId: sitting.sittingId,
            percent: outcome.percent, b: rewardPlan.bNext,
          });
        }
        const userRef = db.doc(`users/${sitting.uid}`);
        const user = data(await tx.get(userRef)) || {};
        const nolics = Math.max(0, (Number(user.nolics) || 0) + rewardPlan.nolics);
        const skill = Math.max(0, (Number(user.pointsSkill ?? user.skillPoints) || 0) + rewardPlan.skillPoints);
        tx.set(userRef, {nolics, pointsNolics: nolics, pointsSkill: skill, skillPoints: skill,
          updatedAtMs: closedAtMs}, {merge: true});
        for (const entry of ledgerEntries({
          uid: sitting.uid, reason: REASON.ATTEMPT_REWARD, sourceId: sitting.sittingId, atMs: closedAtMs,
          deltas: {[CURRENCY.NOLICS]: rewardPlan.nolics, [CURRENCY.SKILL_POINTS]: rewardPlan.skillPoints},
          details: {mode: sitting.mode, assessmentId: sitting.assessmentId},
        })) {
          tx.create(db.doc(`${LEDGER_PATH}/${entry.id}`), entry);
        }
      }

      // Успешный финал: факт выпуска + прочная работа подписывания в том же коммите.
      // Откат терминальной транзакции не оставляет ни успеха, ни награды, ни выпуска.
      if (sitting.mode === "FinalExam" && outcome.passed) {
        const fact = issuanceFact({
          uid: sitting.uid,
          courseId: sitting.assessmentId,
          sittingId: sitting.sittingId,
          resultVersion,
          percent: outcome.percent,
          passMark: sitting.rules.passMarkPercent,
          issuedAtMs: closedAtMs,
          keyId: config.certificateSigningKeyId || null,
          bankSnapshot: {
            manifestHash: sitting.rules.manifestHash,
            courseVersion: sitting.courseVersion || null,
            TE: sitting.bank.TE,
            TH: sitting.bank.TH,
            certification: sitting.certification || null,
          },
        });
        tx.create(db.doc(`exam_certificates/${fact.issuanceId}`), fact);
        tx.create(db.doc(`exam_signing_work/${fact.issuanceId}`), signingWorkRecord(fact));
        return {sittingId, status: "TERMINALIZED", issuanceId: fact.issuanceId,
          percent: outcome.percent, passed: outcome.passed};
      }
      return {sittingId, status: "TERMINALIZED", percent: outcome.percent, passed: outcome.passed};
    });
  }

  /** Поколение агрегата темы: каждая изменяющая входы запись продвигает его. */
  async function advanceThemeGeneration(tx, themeId, atMs) {
    const ref = db.doc(`${THEME_GENERATION_COLLECTION}/${themeId}`);
    const generation = data(await tx.get(ref)) || {generation: 0};
    const next = Math.floor(Number(generation.generation) || 0) + 1;
    tx.set(ref, {generation: next, pendingCorrection: generation.pendingCorrection === true,
      updatedAtMs: atMs});
    return next;
  }

  function nextDeadlineOf(sitting) {
    const current = sitting.slots && sitting.slots[sitting.currentIndex];
    if (!current || current.state !== SLOT_STATE.DELIVERED) return null;
    if (current.ack && current.ack.answerDeadlineMs) return current.ack.answerDeadlineMs;
    if (current.index === 0) return sitting.firstAckGapDeadlineMs || null;
    return current.windowDeadlineMs || null;
  }

  function report(sittingId, code) {
    const record = {component: "exam-terminal", code,
      sittingReference: createHash("sha256").update(sittingId).digest("hex")};
    try {
      log(record);
    } catch (_) { /* Диагностика не отменяет прочную работу. */ }
  }

  /**
   * Обход очереди терминализации — по образцу арены: аренда, fencing nonce, ограниченные шаги.
   *
   * Work-очередь — поле `terminalQueryFlag` сидинга: worker читает страницу кандидатов,
   * каждый шаг читает и CAS-ит свой сидинг сам, поэтому_overlap и потеря аренды сходятся к тем
   * же чекам и результатам.
   */
  async function sweep({maxSittings = MAX_SITTINGS_PER_SWEEP} = {}) {
    const startedAtMs = now();
    const nonce = randomBytes(24).toString("hex");
    const leaseRef = db.doc(WORKER_STATE_PATH);
    const acquired = await db.runTransaction(async (tx) => {
      const state = data(await tx.get(leaseRef)) || {schemaVersion: 1, lease: null};
      const at = now();
      if (state.lease && state.lease.expiresAtMs > at) return {acquired: false};
      tx.set(leaseRef, {...state, lease: {ownerNonce: nonce, acquiredAtMs: at,
        expiresAtMs: at + LEASE_MS}}, {merge: true});
      return {acquired: true};
    });
    if (!acquired.acquired) return {status: "BUSY", attempted: 0};

    let attempted = 0;
    let terminalized = 0;
    let failed = 0;
    try {
      const snapshot = await db.collection(SITTINGS_COLLECTION)
        .where("terminalQueryFlag", "==", TERMINAL_QUERY_TRUE)
        .orderBy("nextDeadlineAtMs")
        .limit(Math.max(1, Math.floor(Number(maxSittings) || 1)))
        .get();
      for (const doc of snapshot.docs) {
        if (now() >= startedAtMs + FUNCTION_TIMEOUT_MS) break;
        attempted += 1;
        try {
          // Очередь не авторитет: шаг перечитывает сидинг и CAS-ит сам.
          const result = await advanceSitting({sittingId: doc.id});
          if (result.status === "TERMINALIZED" || result.status === "CLOSED") {
            // CLOSED: сидинг закрыт (например, коммит прошёл, а снятие флага упало) — очередь
            // обязана отпустить его, иначе ограниченная страница вечно занята.
            if (result.status === "TERMINALIZED") terminalized += 1;
            await db.doc(`${SITTINGS_COLLECTION}/${doc.id}`).set({
              terminalQueryFlag: false,
            }, {merge: true});
          } else if (result.status === "POISON") {
            failed += 1;
          }
        } catch (error) {
          failed += 1;
          report(doc.id, SAFE_ERROR_CODES.has(error.code) ? error.code : "ADVANCE_FAILED");
        }
      }
    } finally {
      await db.runTransaction(async (tx) => {
        const state = data(await tx.get(leaseRef)) || {};
        if (state.lease && state.lease.ownerNonce === nonce) {
          tx.set(leaseRef, {...state, lease: null}, {merge: true});
        }
      });
    }
    return {status: failed === 0 ? "PROCESSED" : "PROCESSED_WITH_ERRORS",
      attempted, terminalized, failed};
  }

  /** Помочь переднему вызову: после ответа/финала сидинг ставится в очередь терминализации. */
  async function enqueueSitting(sittingId, nextDeadlineAtMs) {
    await db.doc(`${SITTINGS_COLLECTION}/${sittingId}`).set({
      terminalQueryFlag: TERMINAL_QUERY_TRUE,
      nextDeadlineAtMs: nextDeadlineAtMs === undefined ? null : nextDeadlineAtMs,
    }, {merge: true});
  }

  return {advanceSitting, sweep, enqueueSitting, advancePlan, terminalOutcome, planReward,
    slotDigit, sittingDigits, CLOSE_REASON};
}

module.exports = {
  ASSESSMENT_STATE_COLLECTION,
  TERMINAL_QUERY_TRUE,
  CLOSE_REASON,
  COVERAGE_COLLECTION,
  FIRST_PASS_AWARDS_COLLECTION,
  FUNCTION_TIMEOUT_MS,
  LEASE_MS,
  MAX_ADVANCES_PER_SITTING,
  MAX_SITTINGS_PER_SWEEP,
  OPEN_SITTINGS_COLLECTION,
  REWARD_RECEIPTS_COLLECTION,
  SITTINGS_COLLECTION,
  TERMINAL_QUERY_TRUE,
  THEME_GENERATION_COLLECTION,
  WORKER_STATE_COLLECTION,
  WORKER_STATE_PATH,
  advanceExpiredSlot,
  advancePlan,
  createExamTerminal,
  planReward,
  slotDigit,
  sittingDigits,
  terminalOutcome,
};
