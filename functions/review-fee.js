"use strict";

/**
 * Платный цикл проверки: фонд цикла делится между проверяющими, остаток сгорает (AD-10, CAP-4).
 *
 * Модель денег принята владельцем (PRD, «Оплата подачи на проверку»): первоначальная полная
 * подача стоит 1000 ноликов, частичная перепроверка после исправлений — 100 ноликов за вопрос;
 * плата привязана к принятой заявке и не возвращается (в том числе при отклонении по качеству и
 * техническом сбое). Вознаграждение проверяющего — личный уровень за урок целиком: уровень 200
 * даёт 20 ноликов независимо от числа проверенных вопросов. Всё, что выплаты не съели, сгорает;
 * равенство «плата = вознаграждения + сгоревший остаток» держится всегда.
 *
 * Что решил AD-10 из открытых вопросов владельца:
 *
 *   — единица вознаграждения — unit=(оплаченный цикл, урок, проверяющий), направления объединяются:
 *     проверивший и тестирование, и перевод получает одну выплату за урок, а не две;
 *   — личный уровень Li фиксирует первый принятый завершённый результат юнита, и поздние записи
 *     с другим уровнем его не двигают;
 *   — вся арифметика — в десятых нолика: при ΣLi ≤ 10P entitlement_i = Li и burn = 10P − ΣLi;
 *     иначе доли 10P×Li/ΣLi берутся floors, а остаточные десятые раздаются по убыванию остатка,
 *     ничья — по стабильному id юнита;
 *   — floating-point запрещён: произведения считаются BigInt-делением, а не числами с плавающей
 *     запятой;
 *   — зачисление в кошелёк credit = floor((oldCarry + entitlement)/10), новый carry — остаток;
 *     carry у проверки свой, отдельный от авторской доли Арены (AD-9);
 *   — до полного цикла финального распределения и сгорания нет (незавершённый цикл — OQ-2, он
 *     здесь не добавлен: пока заявка не PUBLISHED и не REJECTED, фонд лежит нетронутым).
 *
 * Границы, которые модуль не пересекает (честно, а не молча):
 *
 *   — «завершённый результат» сервер видит по-своему: TESTING/LOGIC завершены записью с оценкой
 *     урока (покрытие вопросов приёмник проверяет при записи), TRANSLATION — записью, накрывшей
 *     все вопросы задания содержимым (тот же предикат, что засчитывает язык), TRANSLATION_REVIEW —
 *     записью с явными решениями по сегментам; перечислить сегменты независимо сервер не может —
 *     полнота сегментного ревью принимается на интейке;
 *   — отклонение задания — тоже завершённый результат: судья отклонения получает юнит с уровнем
 *     на момент отклонения (поле задания rejectedReviewerLevelAtSubmit);
 *   — тариф перепроверки считается от всех вопросов заявки: нынешняя модель ревью перепроверяет
 *     каждую новую заявку целиком (записи привязаны к submissionId), значит «вопросов, требующих
 *     повторной проверки», у неё ровно questionCount; настоящий частичный перечёт появится вместе
 *     с моделью сохранных проверок и унесёт свой диф.
 */

const TENTHS_PER_NOLIC = 10;

/** Версия расчёта: immutable manifest называет её, чтобы смена формулы не переписывала прошлое. */
const CALCULATION_VERSION = 1;

/** Принятые тарифы владельца (PRD «Оплата подачи на проверку»), нолики. */
const DEFAULT_TARIFFS = Object.freeze({initialSubmissionNolics: 1000, recheckQuestionNolics: 100});

/**
 * Тарифы из документа `configs/review_fee` — или принятые по умолчанию.
 *
 * Тариф решает судьбу денег, поэтому испорченное значение отказывает громко, а не тихо падает к
 * умолчанию: невидимое для владельца изменение цены хуже честной поломки всех платных подач.
 */
function normalizeReviewFeeTariffs(data) {
  const source = data && typeof data === "object" ? data : {};
  const revision = integerAtLeastZero(source.revision, 1, "revision");
  return {
    initialSubmissionNolics: integerAtLeastZero(
      source.initialSubmissionNolics, DEFAULT_TARIFFS.initialSubmissionNolics, "initialSubmissionNolics"),
    recheckQuestionNolics: integerAtLeastZero(
      source.recheckQuestionNolics, DEFAULT_TARIFFS.recheckQuestionNolics, "recheckQuestionNolics"),
    revision,
  };
}

function integerAtLeastZero(value, fallback, name) {
  if (value === null || value === undefined) return fallback;
  if (!Number.isInteger(value) || value < 0) {
    throw new RangeError(`review fee tariff ${name} must be a non-negative integer, got ${JSON.stringify(value)}`);
  }
  return value;
}

/**
 * Плата за цикл, целые нолики.
 *
 * `priorCycle` — у квеста уже была оплаченная подача или публикация: каждая новая попытка подачи
 * платная (PRD), и тысяча к перепроверке не прибавляется.
 */
function reviewFeeNolics({tariffs, priorCycle, questionCount}) {
  if (!Number.isInteger(questionCount) || questionCount < 1) {
    throw new RangeError(`questionCount must be a positive integer, got ${JSON.stringify(questionCount)}`);
  }
  return priorCycle
    ? tariffs.recheckQuestionNolics * questionCount
    : tariffs.initialSubmissionNolics;
}

/** Целое неотрицательное число — уровень в десятых или плата в десятых. */
function tenths(value, name) {
  if (!Number.isInteger(value) || value < 0) {
    throw new RangeError(`${name} must be a non-negative integer, got ${JSON.stringify(value)}`);
  }
  return value;
}

/** Перенос содержимого: вопрос без текста и без картинки переводом не считается (CAP-3). */
function carriesContent(question) {
  return (question.text !== null && question.text !== undefined && String(question.text).trim().length > 0) ||
    (question.imagePath !== null && question.imagePath !== undefined);
}

/** Вопросов на язык: та же мера полноты перевода, что засчитывает язык в агрегате. */
function sourceQuestionCount(questions) {
  const groups = {};
  for (const question of questions || []) {
    const language = question.language ? String(question.language).trim().toLowerCase() : "";
    groups[language] = (groups[language] || 0) + 1;
  }
  const counts = Object.values(groups);
  return counts.length > 0 ? Math.max(...counts) : (questions || []).length;
}

/**
 * Запись — завершённый результат своего направления.
 *
 * Полнота сегментного ревью сервером не перечисляется (сегменты живут у клиента), поэтому
 * завершённость TRANSLATION_REVIEW — явные решения, принятые на интейке; это граница, а не молчаливое
 * допущение: непустых решений без явного вердикта приёмник не пишет.
 */
function completedRecord(record, task) {
  if (!record || !record.acceptedByServer || !record.reviewerUid) return false;
  if (record.kind === "TESTING" || record.kind === "LOGIC") {
    return record.score !== null && record.score !== undefined;
  }
  if (record.kind === "TRANSLATION") {
    const translated = record.translatedQuestions || [];
    const required = task && Array.isArray(task.questions) ? sourceQuestionCount(task.questions) : 0;
    return required > 0 && translated.length >= required && translated.every(carriesContent);
  }
  if (record.kind === "TRANSLATION_REVIEW") {
    return Array.isArray(record.segmentResults) && record.segmentResults.length > 0;
  }
  return false;
}

/**
 * Юниты цикла: по одному на (урок, проверяющий), направления объединены.
 *
 * Уровень юнита даёт первый по времени завершённый результат (ничья — по id записи): поздний
 * уровень другого направления фикс уже не двигает. Отклонение задания приносит юнит судье —
 * уровень берётся из поля задания `rejectedReviewerLevelAtSubmit`.
 *
 * @param {object} input `{submissionId, lessonIds, taskByLessonId, recordsByLessonId, ownerUid}` —
 *   записи одного цикла; модуль и сам фильтрует по submissionId: чужая заявка чужой цикл не кормит.
 * @returns {Array} юниты `{unitId, lessonId, reviewerUid, levelTenths, evidenceRecordId}` по
 *   возрастанию unitId.
 */
function buildReviewUnits({submissionId, lessonIds, taskByLessonId, recordsByLessonId, ownerUid}) {
  const units = new Map();
  for (const lessonId of lessonIds || []) {
    const task = (taskByLessonId || {})[lessonId] || null;
    for (const record of (recordsByLessonId || {})[lessonId] || []) {
      if (submissionId && record.submissionId !== submissionId) continue;
      if (!completedRecord(record, task)) continue;
      if (ownerUid && record.reviewerUid === ownerUid) continue;
      const unitId = `${lessonId}/${record.reviewerUid}`;
      const previous = units.get(unitId);
      if (!previous || recordWinsLevel(previous, record)) {
        units.set(unitId, {
          unitId,
          lessonId,
          reviewerUid: record.reviewerUid,
          levelTenths: tenths(record.reviewerLevelAtSubmit, `reviewerLevelAtSubmit of ${unitId}`),
          evidenceRecordId: record.id,
          createdAtMs: record.createdAtMs,
        });
      }
    }
    const rejection = task && task.rejectedAtMs ? rejectionUnit(task, lessonId, ownerUid, submissionId) : null;
    if (rejection && !units.has(rejection.unitId)) units.set(rejection.unitId, rejection);
  }
  return Array.from(units.values()).sort((a, b) => (a.unitId < b.unitId ? -1 : a.unitId > b.unitId ? 1 : 0));
}

/**
 * Судья отклонения: юнит с его уровнем на момент отклонения (обоснованное отклонение — работа).
 *
 * Задание, уже переписанное новой заявкой того же урока, своё отклонение с собой унесло: судить
 * его в старом цикле — значит платить за чужой цикл.
 */
function rejectionUnit(task, lessonId, ownerUid, submissionId) {
  if (submissionId && task.submissionId && task.submissionId !== submissionId) return null;
  const reviewerUid = task.rejectedByUid;
  if (!reviewerUid || (ownerUid && reviewerUid === ownerUid)) return null;
  const level = task.rejectedReviewerLevelAtSubmit;
  if (level === null || level === undefined) return null;
  return {
    unitId: `${lessonId}/${reviewerUid}`,
    lessonId,
    reviewerUid,
    levelTenths: tenths(level, `rejectedReviewerLevelAtSubmit of ${lessonId}`),
    evidenceRecordId: null,
  };
}

/**
 * Фиксирует ли запись уровень вместо прежней: берётся более ранний завершённый результат,
 * одновременные — по меньшему id записи. Юнит от отклонения записи не имеет и не соревнуется:
 * он появляется, только когда записей у судьи нет вовсе.
 */
function recordWinsLevel(previous, candidate) {
  if (candidate.createdAtMs !== previous.createdAtMs) {
    return candidate.createdAtMs < previous.createdAtMs;
  }
  return String(candidate.id) < String(previous.evidenceRecordId);
}

/**
 * Распределение фонда по юнитам, в десятых нолика (AD-10).
 *
 * Две ветки правила: при ΣLi ≤ 10P каждый уровень уходит целиком и остаток сгорает; при ΣLi > 10P
 * доли floor(10P×Li/ΣLi) дополняются остаточными десятыми по убыванию остатка, ничья — по unitId.
 * Произведения — BigInt: дробей и потерь нет ни на одном размере.
 *
 * @returns {{allocations: Array, sumEntitlementTenths: number, burnTenths: number}}
 */
function settleReviewFeeTenths({feeTenths, units}) {
  tenths(feeTenths, "feeTenths");
  for (const unit of units || []) tenths(unit.levelTenths, `levelTenths of ${unit.unitId}`);
  const ordered = [...(units || [])].sort((a, b) => (a.unitId < b.unitId ? -1 : a.unitId > b.unitId ? 1 : 0));
  const totalLevels = ordered.reduce((sum, unit) => sum + unit.levelTenths, 0);

  if (totalLevels <= feeTenths) {
    const allocations = ordered.map((unit) => ({
      unitId: unit.unitId,
      levelTenths: unit.levelTenths,
      entitlementTenths: unit.levelTenths,
      remainderTenths: 0,
    }));
    const sumEntitlementTenths = totalLevels;
    return {allocations, sumEntitlementTenths, burnTenths: feeTenths - sumEntitlementTenths};
  }

  const bigTotal = BigInt(totalLevels);
  const shares = ordered.map((unit) => {
    const product = BigInt(feeTenths) * BigInt(unit.levelTenths);
    return {unit, share: Number(product / bigTotal), remainder: product % bigTotal};
  });
  const distributed = shares.reduce((sum, item) => sum + item.share, 0);
  let leftover = feeTenths - distributed;
  if (leftover < 0 || leftover > shares.length) {
    throw new RangeError(`leftover tenths ${leftover} out of range for ${shares.length} units`);
  }
  const order = shares
    .map((item, index) => ({index, remainder: item.remainder}))
    .sort((a, b) => (a.remainder !== b.remainder
      ? (a.remainder > b.remainder ? -1 : 1)
      : compareUnitId(ordered[a.index].unitId, ordered[b.index].unitId)));
  const boosted = new Set(order.slice(0, leftover).map((item) => item.index));
  const allocations = shares.map((item, index) => ({
    unitId: item.unit.unitId,
    levelTenths: item.unit.levelTenths,
    entitlementTenths: item.share + (boosted.has(index) ? 1 : 0),
    remainderTenths: Number(item.remainder),
  }));
  const sumEntitlementTenths = allocations.reduce((sum, item) => sum + item.entitlementTenths, 0);
  return {allocations, sumEntitlementTenths, burnTenths: feeTenths - sumEntitlementTenths};
}

function compareUnitId(a, b) {
  return a < b ? -1 : a > b ? 1 : 0;
}

/**
 * Зачисление с переносом: credit = floor((oldCarry + entitlement)/10), carry — остаток.
 *
 * Носитель carry — кошелёк проверяющего; инвариант AD-10 `oldCarry + entitlement =
 * 10×wholeCredits + newCarry` держится тождественно.
 */
function walletCreditForEntitlement({priorCarryTenths, entitlementTenths}) {
  tenths(priorCarryTenths, "priorCarryTenths");
  tenths(entitlementTenths, "entitlementTenths");
  const total = priorCarryTenths + entitlementTenths;
  const creditedNolics = Math.floor(total / TENTHS_PER_NOLIC);
  return {creditedNolics, nextCarryTenths: total - creditedNolics * TENTHS_PER_NOLIC};
}

/**
 * Итоговый манифест расчёта: фиксирует юниты, зачисления, carry и сгоревший остаток.
 *
 * Инварианты проверяются здесь, а не верятся: манифест неизменяем, и неверный манифест должен
 * уронить расчёт, а не лечь в историю консервативным.
 */
function buildSettlementManifest({submissionId, ownerUid, questId, outcome, settledAtMs,
  feeNolics, tariff, tariffRevision, units, allocations, wallets}) {
  const feeTenths = tenths(feeNolics, "feeNolics") * TENTHS_PER_NOLIC;
  const byUnit = new Map((allocations || []).map((item) => [item.unitId, item]));
  const resolvedUnits = (units || []).map((unit) => {
    const allocation = byUnit.get(unit.unitId);
    if (!allocation) throw new RangeError(`unit ${unit.unitId} has no allocation`);
    return {
      unitId: unit.unitId,
      lessonId: unit.lessonId,
      reviewerUid: unit.reviewerUid,
      levelTenths: unit.levelTenths,
      entitlementTenths: allocation.entitlementTenths,
      evidenceRecordId: unit.evidenceRecordId,
    };
  });
  const sumEntitlementTenths = resolvedUnits.reduce((sum, unit) => sum + unit.entitlementTenths, 0);
  const burnTenths = feeTenths - sumEntitlementTenths;
  if (burnTenths < 0) throw new RangeError(`entitlements ${sumEntitlementTenths} exceed fee ${feeTenths} tenths`);

  const entitlementByReviewer = new Map();
  for (const unit of resolvedUnits) {
    entitlementByReviewer.set(
      unit.reviewerUid,
      (entitlementByReviewer.get(unit.reviewerUid) || 0) + unit.entitlementTenths,
    );
  }
  const resolvedWallets = (wallets || []).map((wallet) => {
    const entitlementTenths = entitlementByReviewer.get(wallet.uid) || 0;
    const credit = walletCreditForEntitlement({
      priorCarryTenths: wallet.priorCarryTenths,
      entitlementTenths,
    });
    if (credit.nextCarryTenths !== wallet.nextCarryTenths || credit.creditedNolics !== wallet.creditedNolics) {
      throw new RangeError(`wallet ${wallet.uid} disagrees with the carry equation`);
    }
    return {
      uid: wallet.uid,
      entitlementTenths,
      priorCarryTenths: wallet.priorCarryTenths,
      creditedNolics: credit.creditedNolics,
      nextCarryTenths: credit.nextCarryTenths,
    };
  });
  const settledEntitlements = resolvedWallets.reduce((sum, wallet) => sum + wallet.entitlementTenths, 0);
  if (settledEntitlements !== sumEntitlementTenths) {
    throw new RangeError(`wallets cover ${settledEntitlements} of ${sumEntitlementTenths} entitlement tenths`);
  }

  return {
    submissionId,
    ownerUid,
    questId,
    outcome,
    settledAtMs,
    feeNolics,
    feeTenths,
    calculationVersion: CALCULATION_VERSION,
    tariff,
    tariffRevision,
    units: resolvedUnits,
    wallets: resolvedWallets,
    sumEntitlementTenths,
    burnTenths,
  };
}

module.exports = {
  CALCULATION_VERSION,
  DEFAULT_TARIFFS,
  TENTHS_PER_NOLIC,
  buildReviewUnits,
  buildSettlementManifest,
  completedRecord,
  normalizeReviewFeeTariffs,
  reviewFeeNolics,
  settleReviewFeeTenths,
  walletCreditForEntitlement,
};
