"use strict";

/**
 * Оценщик допуска к экзамену — чистые типизированные вердикты без побочных эффектов.
 *
 * Один и тот же затвор читают и старт сидинга (внутри транзакции, до списания), и витрина
 * доступности: два ответа на вопрос «можно ли начать» разошлись бы в тот день, когда игрок
 * увидел бы кнопку, которую сервер не станет исполнять.
 *
 * Порядок затворов — порядок протокола (§Admission):
 *
 * 1. **Открытый сидинг — восстановление.** Одна открытая попытка на пару пользователь/оценка;
 *   старт при открытой возвращает её с исходными дедлайнами, без второго списания и без
 *   остальных затворов: восстановление не вторая попытка.
 * 2. **Явная недоступность сервера.** Экзамен выключен ручкой, курс не найден, манифест
 *   неизвестен, основа стоимости двусмысленна, поколение коррекции не готово — `unavailable`,
 *   и ни одно из них не прикидывается ни нулём, ни коротким набором, ни истёкшим кулдауном.
 * 3. **Условия.** Изменённые платные условия требуют нового согласия — `Locked` с причиной.
 * 4. **Банк.** Набор короче границы — `PoolTooSmall`; финал без сертификационной пригодности
 *   курса и двусмысленность основы — явный `unavailable`.
 * 5. **Прогресс.** Тема: непокрытый требуемый EASY — `Locked`; собранное, но непроглоченное —
 *   `ResultsNotSynced`. Финал: непройденная обязательная тема — `Locked`; ожидающее поколение
 *   коррекции — `unavailable`.
 * 6. **Кулдаун.** Терминальное закрытие + 24ч (тема) / 168ч (финал); грань включена, полночь и
 *   смена недели ничего не сбрасывают.
 * 7. **Плата.** Только СТАНДАРТНЫЕ очки с запасом после доводки регенерации; плазма плату за
 *   экзамен не платит никогда. Не хватает — `InsufficientCharge`, ничего не списано.
 *
 * Чистый модуль: тестируется без firebase-admin.
 */

/** Прокрутка кулдауна протокола: тема — сутки, финал — семь суток, от терминального закрытия. */
const THEME_COOLDOWN_MS = 24 * 60 * 60 * 1000;
const FINAL_COOLDOWN_MS = 168 * 60 * 60 * 1000;

const OUTCOME = Object.freeze({
  OK: "ok",
  RECOVER: "recover",
  LOCKED: "Locked",
  RESULTS_NOT_SYNCED: "ResultsNotSynced",
  COOLDOWN: "Cooldown",
  INSUFFICIENT_CHARGE: "InsufficientCharge",
  POOL_TOO_SMALL: "PoolTooSmall",
  UNAVAILABLE: "unavailable",
});

/** Согласие на платные условия: пересогласование требуется, когда ревизия условий изменилась. */
function termsVerdict({requiredTermsRevision, acceptedTermsRevision, shownTerms}) {
  if (shownTerms !== true) return {accepted: false, reason: "TERMS_NOT_SHOWN"};
  const required = Math.floor(Number(requiredTermsRevision) || 0);
  const accepted = Math.floor(Number(acceptedTermsRevision) || 0);
  if (accepted < required) return {accepted: false, reason: "TERMS_CHANGED"};
  return {accepted: true, reason: "ok"};
}

/**
 * Кулдаун: доступен ли старт к моменту `nowMs`.
 *
 * `closedAtMs` — момент терминального закрытия прошлой попытки (включая таймаут и брошенность).
 * Грань включена: T+24h ровно — доступно. Полночь, неделя и часовой пояс не участвуют.
 */
function cooldownVerdict({closedAtMs, cooldownMs, nowMs}) {
  const closed = Math.floor(Number(closedAtMs) || 0);
  const span = Math.floor(Number(cooldownMs) || 0);
  const now = Math.floor(Number(nowMs) || 0);
  if (closed <= 0 || span <= 0) return {available: true, availableAtMs: null};
  const availableAtMs = closed + span;
  return {available: now >= availableAtMs, availableAtMs};
}

/**
 * Полный вердикт допуска.
 *
 * @param params все входы читает вызывающий; сюда — только готовые факты:
 *   - `openSitting` — открытый сидинг пары пользователь/оценка или null;
 *   - `examEnabled` — ручка сервера (explicit unavailable state);
 *   - `bankKnown` — банк прочитан и однозначен (курс найден, основа стоимости посчиталась,
 *     поколение коррекции готово к проверке);
 *   - `terms` — {requiredTermsRevision, acceptedTermsRevision, shownTerms};
 *   - `pool` — {drawable: boolean} границы набора;
 *   - `certification` — для финала: {eligible: boolean} из банка; для темы не читается;
 *   - `coverage` — для темы: {state} по КАЖДОМУ уроку темы, худший из вердиктов; для финала:
 *     {complete, pendingGeneration} реестра пройденных тем;
 *   - `cooldown` — {closedAtMs} последнего терминального закрытия пары;
 *   - `lifePoints` — баланс СТАНДАРТНЫХ очков после доводки регенерации; `price` — цена оценки.
 * @returns {{kind: string, reason?: string, availableAtMs?: number|null, sittingId?: string,
 *   price?: number}}
 */
function evaluateAdmission(params) {
  const {
    openSitting,
    examEnabled = true,
    bankKnown = true,
    terms,
    pool,
    certification,
    coverage,
    cooldown,
    lifePoints,
    price,
    nowMs,
  } = params;

  // 1. Восстановление: открытый сидинг сильнее всех остальных затворов.
  if (openSitting && openSitting.state === "OPEN") {
    return {kind: OUTCOME.RECOVER, sittingId: openSitting.sittingId, price: 0,
      reason: "open sitting recovered"};
  }

  // 2. Явная недоступность сервера — до всякой другой причины.
  if (examEnabled !== true) return {kind: OUTCOME.UNAVAILABLE, reason: "EXAM_DISABLED"};
  if (bankKnown !== true) return {kind: OUTCOME.UNAVAILABLE, reason: "BANK_UNAVAILABLE"};

  // 3. Условия: согласие должно быть показано и покрывать текущую ревизию.
  const termsOutcome = termsVerdict(terms || {});
  if (!termsOutcome.accepted) {
    return {kind: OUTCOME.LOCKED, reason: termsOutcome.reason};
  }

  // 4. Банк: границы набора и сертификационная пригодность.
  if (!pool || pool.drawable !== true) return {kind: OUTCOME.POOL_TOO_SMALL, reason: "DRAW_GATE"};
  if (certification && certification.eligible !== true) {
    return {kind: OUTCOME.UNAVAILABLE, reason: "COURSE_NOT_CERTIFICATION_ELIGIBLE"};
  }

  // 5. Прогресс.
  if (coverage) {
    if (coverage.pendingGeneration === true) {
      return {kind: OUTCOME.UNAVAILABLE, reason: "CORRECTION_PENDING"};
    }
    if (coverage.complete !== true) {
      // Худший из вердиктов покрытия решает: непроглоченное доказательство — ожидание
      // синхронизации; неизвестный манифест — недоступность; остальное — замок непройденного
      // содержимого.
      if (coverage.state === "ResultsNotSynced") {
        return {kind: OUTCOME.RESULTS_NOT_SYNCED, reason: "COVERAGE_PENDING_MATERIALIZATION"};
      }
      if (coverage.state === "Unavailable") {
        return {kind: OUTCOME.UNAVAILABLE, reason: "COVERAGE_UNKNOWN"};
      }
      return {kind: OUTCOME.LOCKED, reason: "COVERAGE_INCOMPLETE"};
    }
  }

  // 6. Кулдаун: от терминального закрытия, грань включена, календарь не участвует.
  const cooldownOutcome = cooldownVerdict({
    closedAtMs: cooldown ? cooldown.closedAtMs : null,
    cooldownMs: params.cooldownMs || 0,
    nowMs,
  });
  if (!cooldownOutcome.available) {
    return {kind: OUTCOME.COOLDOWN, availableAtMs: cooldownOutcome.availableAtMs};
  }

  // 7. Плата: только стандартные очки; отказ ничего не списывает.
  const points = Math.floor(Number(lifePoints) || 0);
  const cost = Math.floor(Number(price) || 0);
  if (points < cost) return {kind: OUTCOME.INSUFFICIENT_CHARGE, reason: "STANDARD_POINTS", price: cost};

  return {kind: OUTCOME.OK, price: cost};
}

/** Худший из вердиктов покрытия уроков темы — то, что видит затвор прогресса. */
function worstCoverage(lessonVerdicts) {
  // Принимаются и готовые вердикты, и их состояния: вызывающий часто собирает только строки.
  const states = (Array.isArray(lessonVerdicts) ? lessonVerdicts : [])
    .map((verdict) => (verdict && typeof verdict === "object" ? verdict.state : verdict));
  if (states.length === 0) return {complete: false, state: "Unavailable"};
  if (states.includes("ResultsNotSynced")) return {complete: false, state: "ResultsNotSynced"};
  if (states.includes("Unavailable")) return {complete: false, state: "Unavailable"};
  if (states.every((state) => state === "ok")) return {complete: true, state: "ok"};
  return {complete: false, state: "Locked"};
}

module.exports = {
  FINAL_COOLDOWN_MS,
  OUTCOME,
  THEME_COOLDOWN_MS,
  cooldownVerdict,
  evaluateAdmission,
  termsVerdict,
  worstCoverage,
};
