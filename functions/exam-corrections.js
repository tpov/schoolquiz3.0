"use strict";

const {createHash, randomBytes} = require("node:crypto");
const {correctedPercent, themeStarsTenths, passedAgainst, safeAdvice} = require("./exam-scoring");
const {SITTINGS_COLLECTION, SITTING_STATE, ASSESSMENT_STATE_COLLECTION} = require("./exam-sitting");
const {assessmentStateId} = require("./exam-coverage");

/**
 * Коррекции C2 — подотчётный пересчёт подтверждённой ошибки содержимого.
 *
 * Корректируется ОЦЕНКА, не деньги и не свидетельство: исключения накладываются на ИСХОДНЫЕ
 * слоты замороженного сидинга, пересчёт идёт через общий скорер с вложенным усечением и
 * захваченным проходным баллом, и ни одна уже выплаченная нолик или XP не двигается.
 *
 * Пять правил (C2; протокол §Progress/quality/corrections):
 *
 * - **Только авторизованное исключение.** Подтверждённая ошибка ревизии; статистика, поздняя
 *   правка или просьба клиента исключением не являются. Запись коррекции подотчётна: кто, когда,
 *   почему, по какому свидетельству.
 * - **Накопление с CAS головы.** Коррекции дописываются; голова коррекции публикуется
 *   сравнением-с-обменом по версии. Тот же correctionId с тем же телом переигрывает тот же
 *   эффект; конфликтное переиспользование — отказ. Порядок исключений цену не двигает.
 * - **Барьер поколения.** Общее поколение агрегата темы покрывает КАЖДУЮ изменяющую входы
 *   запись, включая новый терминальный сидинг темы при незакрытой коррекции. Материализатор
 *   публикует только для всё ещё текущего поколения: устаревший пересчёт отклоняется и
 *   пересчитывается заново. Новый старт финала видит незавершённое поколение ДО списания.
 * - **Деньги нетронуты.** Ни доплаты, ни удержания, ни переоценки, ни второго чека; история
 *   учтённого максимума не сдвигается ни вверх, ни вниз. Исправленная коррекцией первая тема-
 *   проход платит разовую награду только если чека первого прохода не существует — по исходной
 *   замороженной базе, с b=0.
 * - **Неоценимое — без числа.** `UNSCORABLE_ALL_EXCLUDED` (все слоты исключены) и
 *   `CORRECTION_PENDING_EVIDENCE` (свидетельство отсутствует или испорчено) не несут ни
 *   процента, ни прохода, ни звёзд — и никогда не прикидываются нулём, сотней или старым
 *   результатом.
 *
 * Чистый модуль: Firestore подводит фабрика.
 */

const CORRECTIONS_COLLECTION = "exam_corrections";
const CORRECTION_HEADS_COLLECTION = "exam_correction_heads";
const THEME_GENERATION_COLLECTION = "exam_theme_generation";
const CORRECTION_POLICY_VERSION = 1;

const CORRECTION_STATE = Object.freeze({
  SCORED: "SCORED",
  UNSCORABLE_ALL_EXCLUDED: "UNSCORABLE_ALL_EXCLUDED",
  CORRECTION_PENDING_EVIDENCE: "CORRECTION_PENDING_EVIDENCE",
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

/**
 * Запись одного авторизованного исключения.
 *
 * `slotIndex` адресует ИСХОДНЫЙ слот сидинга; `originalDigest` связывает запись с
 * свидетельством, по которому она судила. Тот же correctionId с тем же телом — переигрывание;
 * с другим — конфликт.
 */
function correctionRecord({correctionId, uid, mode, assessmentId, sittingId, slotIndex,
  actorUid, reason, reviewedAtMs, originalDigest}) {
  const index = Math.floor(Number(slotIndex));
  if (!Number.isSafeInteger(index) || index < 0 || index > 19) {
    fail("INVALID_ARGUMENT", "slotIndex must address an original slot 0..19");
  }
  if (typeof correctionId !== "string" || !correctionId) fail("INVALID_ARGUMENT", "correctionId is required");
  if (typeof reason !== "string" || !reason.trim()) fail("INVALID_ARGUMENT", "reason is required");
  const reviewedAt = Math.floor(Number(reviewedAtMs) || 0);
  return {
    correctionId,
    schemaVersion: 1,
    uid,
    mode,
    assessmentId,
    sittingId,
    slotIndex: index,
    actorUid: actorUid || null,
    reason,
    reviewedAtMs: reviewedAt,
    originalDigest: originalDigest || null,
    correctionPolicyVersion: CORRECTION_POLICY_VERSION,
  };
}

/** Тело переигрывания: запись минус подотчётные метки времени исполнения. */
function correctionReplayKey(record) {
  const {correctionId, uid, mode, assessmentId, sittingId, slotIndex, reason,
    correctionPolicyVersion} = record;
  return digest({correctionId, uid, mode, assessmentId, sittingId, slotIndex, reason,
    correctionPolicyVersion});
}

/**
 * Накопленные исключения — различающиеся ИСХОДНЫЕ слоты, порядок независим.
 */
function cumulativeExclusions(correctionRecords) {
  const indexes = new Set();
  for (const record of Array.isArray(correctionRecords) ? correctionRecords : []) {
    if (!record) continue;
    const index = Math.floor(Number(record.slotIndex));
    if (!Number.isSafeInteger(index) || index < 0 || index > 19) {
      fail("CORRUPT_EXCLUSION", `exclusion index out of range: ${record.slotIndex}`);
    }
    indexes.add(index);
  }
  return [...indexes].sort((a, b) => a - b);
}

/**
 * Пересчёт результата по исходным цифрам сидинга и накопленным исключениям.
 *
 * Цифры берутся из замороженного свидетельства: отвеченные — их скоринговые цифры,
 * неотвеченные/просроченные — 1 (обычный нижний балл, в знаменателе). Испорченное
 * свидетельство (позиция без цифры 1..9, число слотов не 20) — ожидание свидетельства, а не
 * укороченный знаменатель.
 *
 * @returns {{state, percent, denominator, passed, starsTenths, advice}|{state:
 *   "CORRECTION_PENDING_EVIDENCE", reason}}
 */
function recomputeResult({sitting, exclusions, adviceMode}) {
  if (!sitting || !Array.isArray(sitting.slots) || sitting.slots.length !== 20) {
    return {state: CORRECTION_STATE.CORRECTION_PENDING_EVIDENCE, reason: "SITTING_EVIDENCE_MISSING"};
  }
  const digits = [];
  for (const slot of sitting.slots) {
    const digit = slot && typeof slot.scoreDigit === "number" ? slot.scoreDigit : null;
    if (!Number.isInteger(digit) || digit < 1 || digit > 9) {
      return {state: CORRECTION_STATE.CORRECTION_PENDING_EVIDENCE, reason: "SLOT_DIGIT_CORRUPT"};
    }
    digits.push(digit);
  }
  let aggregate;
  try {
    aggregate = correctedPercent(digits, exclusions);
  } catch (_) {
    return {state: CORRECTION_STATE.CORRECTION_PENDING_EVIDENCE, reason: "EXCLUSION_INDEX_CORRUPT"};
  }
  if (aggregate.state === "UNSCORABLE_ALL_EXCLUDED") {
    return {state: CORRECTION_STATE.UNSCORABLE_ALL_EXCLUDED, percent: null,
      denominator: 0, passed: null, starsTenths: null, advice: safeNoScoreAdvice(sitting.mode)};
  }
  const passMark = sitting.rules && Number.isInteger(sitting.rules.passMarkPercent) ?
    sitting.rules.passMarkPercent : null;
  if (passMark === null) {
    return {state: CORRECTION_STATE.CORRECTION_PENDING_EVIDENCE, reason: "PASS_MARK_MISSING"};
  }
  const passed = passedAgainst(aggregate.percent, passMark);
  const stars = sitting.mode === "ThemeTest" ? themeStarsTenths(aggregate.percent) : null;
  return {
    state: CORRECTION_STATE.SCORED,
    percent: aggregate.percent,
    denominator: aggregate.denominator,
    passed,
    starsTenths: stars,
    advice: safeAdvice({percent: aggregate.percent, passed,
      mode: adviceMode || sitting.mode || "ThemeTest"}),
  };
}

/** Совет неоценимого состояния: фиксированной длины, без чисел и без имён. */
function safeNoScoreAdvice(mode) {
  return ["UNSCORED", mode === "FinalExam" ? "FINAL_RESULT" : "THEME_RESULT", "CONTACT_SUPPORT"];
}

/**
 * Исправленная проекция результата — публичная половина головы коррекции.
 *
 * Коррекция FAILED→PASSING финала сертификат не создаёт и не создаёт новый порядок заработка:
 * entitlement остаётся за новым успешным сидингом.
 */
function correctedProjection({sitting, resultVersion, outcome, correctedAtMs}) {
  const base = {
    schemaVersion: 1,
    sittingId: sitting.sittingId,
    mode: sitting.mode,
    assessmentId: sitting.assessmentId,
    resultVersion,
    corrected: true,
    correctedAtMs,
    correctionPolicyVersion: CORRECTION_POLICY_VERSION,
    // Идентичность исходного результата рядом: история не стирается коррекцией.
    originalResultVersion: sitting.terminal ? sitting.terminal.resultVersion : null,
  };
  if (outcome.state !== CORRECTION_STATE.SCORED) {
    return {...base, state: outcome.state, percent: null, passed: null, starsTenths: null,
      advice: outcome.advice, denominator: outcome.denominator === undefined ? null : outcome.denominator};
  }
  return {...base, state: CORRECTION_STATE.SCORED, percent: outcome.percent,
    passed: outcome.passed, starsTenths: outcome.starsTenths, advice: outcome.advice,
    denominator: outcome.denominator};
}

/**
 * Фабрика коррекций. `authorizeExclusion` — шов авторизации (ревизия/актёры проверяет
 * вызывающая обвязка; здесь только подотчётная запись), остальное — Firestore.
 */
function createExamCorrections({db, nowMs = () => Date.now(), authorizeExclusion}) {
  if (!db || typeof db.runTransaction !== "function" || typeof authorizeExclusion !== "function") {
    throw new TypeError("exam corrections require db and authorizeExclusion");
  }
  const now = () => Math.floor(nowMs());

  /** Идентичность головы коррекции — та же пара пользователь/оценка, что у состояния допуска. */
  function headId(uid, mode, assessmentId) {
    return digest(["exam-correction-head", uid, mode, assessmentId]);
  }

  /**
   * Дописать авторизованное исключение.
   *
   * Одна транзакция: переигрывание по correctionId, CAS головы, отметка поколения темы как
   * ожидающего коррекцию. Пересчёт здесь НЕ выполняется: голова публикуется с ожидающим
   * поколением, материализатор доводит проекцию отдельно — крах после головы не показывает
   * старый проход как действующий.
   */
  async function appendExclusion({record, expectedHeadVersion}) {
    const verified = authorizeExclusion(record);
    if (verified !== true) fail("NOT_AUTHORIZED", "exclusion was not authorized");
    const body = correctionRecord(record);
    const correctionRef = db.doc(`${CORRECTIONS_COLLECTION}/${body.correctionId}`);
    const headRef = db.doc(`${CORRECTION_HEADS_COLLECTION}/${headId(body.uid, body.mode, body.assessmentId)}`);
    const generationRef = body.mode === "ThemeTest" ?
      db.doc(`${THEME_GENERATION_COLLECTION}/${body.assessmentId}`) : null;
    return db.runTransaction(async (tx) => {
      const at = now();
      const existing = (await tx.get(correctionRef)).data() || null;
      if (existing) {
        if (correctionReplayKey(existing) !== correctionReplayKey(body)) {
          fail("CORRECTION_REUSE", "correctionId is already used by another body");
        }
        return {applied: false, replay: true, head: (await tx.get(headRef)).data() || null};
      }
      const head = (await tx.get(headRef)).data() || null;
      const headVersion = head ? head.headVersion : 0;
      if (Number.isSafeInteger(expectedHeadVersion) && expectedHeadVersion !== headVersion) {
        fail("HEAD_CONFLICT", `correction head moved to ${headVersion}`);
      }
      const exclusions = cumulativeExclusions([...(head ? head.corrections : []), body]);
      const nextHead = {
        headId: headRef.id,
        uid: body.uid,
        mode: body.mode,
        assessmentId: body.assessmentId,
        headVersion: headVersion + 1,
        corrections: [...(head ? head.corrections : []), body],
        cumulativeExclusions: exclusions,
        updatedAtMs: at,
      };
      tx.create(correctionRef, body);
      tx.set(headRef, nextHead);
      if (generationRef) {
        // Поколение помечается ожидающим: новый финал увидит барьер ДО списания, а
        // материализатор знает, что проекцию надо доводить.
        const generation = (await tx.get(generationRef)).data() || {generation: 0};
        tx.set(generationRef, {...generation, pendingCorrection: true, updatedAtMs: at}, {merge: true});
      }
      return {applied: true, replay: false, head: nextHead};
    });
  }

  /** Верх чтения терминальных тем-результатов пользователя при пересборке лучшего: выше — яд. */
  const MAX_THEME_SITTING_RESULTS = 50;

  /**
   * Материализация головы: пересчёт из исходных слотов, публикация исправленной проекции и
   * СОГЛАСОВАННАЯ проекция допуска — всё в одной транзакции.
   *
   * Входы читаются ВНУТРИ транзакции — голова, поколение и само свидетельство сидинга: гонка с
   * новым терминальным проходом разрешается сериализацией на документе поколения (терминальный
   * писатель его тоже пишет), а не доверием к устаревшей копии сидинга у вызвавшего.
   *
   * Для темы состояние допуска пользователя пересобирается здесь же: usable latest results по
   * закрытым сидингам (у исправленного сидинга — пересчитанный исход), лучший процент и проход
   * поверх них, и `passedAtGeneration` = опубликованное поколение, когда проход есть. Ничья
   * история денег эти записи не задевают: чеки, суммы и учтённый максимум не переписываются.
   *
   * Возвращает {published: true, projection, generation, state?} либо
   * {published: false, reason: "STALE_GENERATION"|"NO_HEAD"|"NO_SITTING"}: устаревший пересчёт
   * не публикуется никогда — повтор считывает свежие входы заново.
   */
  async function materialize({uid, mode, assessmentId}) {
    const headRef = db.doc(`${CORRECTION_HEADS_COLLECTION}/${headId(uid, mode, assessmentId)}`);
    const generationRef = mode === "ThemeTest" ?
      db.doc(`${THEME_GENERATION_COLLECTION}/${assessmentId}`) : null;
    return db.runTransaction(async (tx) => {
      const at = now();
      const head = (await tx.get(headRef)).data() || null;
      if (!head) return {published: false, reason: "NO_HEAD"};
      const sittingRef = db.doc(`exam_sittings/${head.corrections[0].sittingId}`);
      const sitting = (await tx.get(sittingRef)).data() || null;
      if (!sitting) return {published: false, reason: "NO_SITTING"};
      const exclusions = cumulativeExclusions(head.corrections);
      const outcome = recomputeResult({sitting, exclusions});
      const resultVersion = (sitting.terminal ? sitting.terminal.resultVersion : 0) + head.headVersion;
      const projection = correctedProjection({sitting, resultVersion, outcome, correctedAtMs: at});
      if (mode !== "ThemeTest" || !generationRef) {
        return {published: true, outcome, projection};
      }
      const generationSnapshot = (await tx.get(generationRef)).data() || {generation: 0};
      if (generationSnapshot.pendingCorrection !== true) {
        return {published: false, reason: "STALE_GENERATION"};
      }
      const publishedGeneration = generationSnapshot.generation + 1;
      // Согласованная проекция допуска: usable latest results по закрытым сидингам темы;
      // исправленный сидинг входит пересчитанным исходом.
      const resultsSnapshot = await tx.get(db.collection(SITTINGS_COLLECTION)
        .where("uid", "==", uid)
        .where("mode", "==", "ThemeTest")
        .where("assessmentId", "==", assessmentId)
        .where("state", "==", SITTING_STATE.CLOSED)
        .limit(MAX_THEME_SITTING_RESULTS));
      if (resultsSnapshot.docs.length >= MAX_THEME_SITTING_RESULTS) {
        fail("CORRECTION_EVIDENCE_OVERFLOW",
          `theme ${assessmentId} has more closed sittings than one bounded recompute reads`);
      }
      let bestPercent = 0;
      let anyPassed = false;
      for (const doc of resultsSnapshot.docs) {
        const row = doc.data() || {};
        const isCorrected = doc.id === sitting.sittingId;
        const usable = isCorrected ? outcome :
          (row.terminal && row.terminal.projection ?
            {state: CORRECTION_STATE.SCORED, percent: row.terminal.projection.percent,
              passed: row.terminal.projection.passed === true} :
            {state: CORRECTION_STATE.CORRECTION_PENDING_EVIDENCE});
        if (usable.state !== CORRECTION_STATE.SCORED) continue;
        bestPercent = Math.max(bestPercent, usable.percent);
        anyPassed = anyPassed || usable.passed === true;
      }
      const stateRef = db.doc(`${ASSESSMENT_STATE_COLLECTION}/${assessmentStateId(uid, mode, assessmentId)}`);
      // Лучший процент пересобран по usable-исходам всех закрытых сидингов темы: исход,
      // который коррекция сделала непригодным, в максимум больше не входит.
      tx.set(stateRef, {
        uid, mode, assessmentId,
        bestPercent,
        passed: anyPassed,
        // Проход маркируется опубликованным поколением; без прохода метка снимается —
        // устаревшая метка не должна выглядеть как ожидающая коррекция.
        passedAtGeneration: anyPassed ? publishedGeneration : null,
        correctedAtGeneration: publishedGeneration,
        updatedAtMs: at,
      }, {merge: true});
      // Барьер поколения: публикация продвигает поколение и снимает ожидание ровно один раз.
      // Гонка с новым терминальным проходом сериализуется на этом же документе.
      tx.set(generationRef, {
        generation: publishedGeneration,
        pendingCorrection: false,
        updatedAtMs: at,
      });
      return {published: true, outcome, projection, generation: publishedGeneration,
        state: {bestPercent: bestPercent, passed: anyPassed,
          passedAtGeneration: anyPassed ? publishedGeneration : null}};
    });
  }

  return {appendExclusion, materialize, headId, CORRECTION_STATE};
}

module.exports = {
  CORRECTION_HEADS_COLLECTION,
  CORRECTION_POLICY_VERSION,
  CORRECTIONS_COLLECTION,
  CORRECTION_STATE,
  THEME_GENERATION_COLLECTION,
  createExamCorrections,
  correctedProjection,
  correctionRecord,
  correctionReplayKey,
  cumulativeExclusions,
  digest,
  recomputeResult,
  safeNoScoreAdvice,
};
