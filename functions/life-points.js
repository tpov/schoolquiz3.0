"use strict";

/**
 * Life points ("жизни") — the activity budget.
 *
 * Model inherited from the legacy app: a heart is a *slot* worth LIFE_POINTS_PER_HEART points,
 * and playing costs points, not whole hearts. Owning more heart slots raises the ceiling, which
 * is what the shop's STANDARD_HEART_SLOT purchase buys.
 *
 * Regeneration is computed lazily instead of by a scheduled job: every read/write derives the
 * current balance from the stored value and the elapsed time. Nothing has to run while the user
 * is away, and the result is identical.
 *
 * These functions are pure so they can be tested without firebase-admin.
 */

const LIFE_POINTS_PER_HEART = 100;
/** One heart regenerates in this many milliseconds. */
const HEART_REGEN_MS = 60 * 60 * 1000;
/** Time to earn a single point. Derived so the two constants above stay the single source. */
const LIFE_POINT_INTERVAL_MS = HEART_REGEN_MS / LIFE_POINTS_PER_HEART;

/** Cost of one lesson attempt, from the legacy price list (CoastValuesLife). */
const LESSON_ATTEMPT_LIFE_COST = 33;

/** Ceiling: every owned heart slot holds LIFE_POINTS_PER_HEART points. */
function maxLifePoints(standardHearts) {
  const hearts = Math.max(0, Math.floor(Number(standardHearts) || 0));
  return hearts * LIFE_POINTS_PER_HEART;
}

/**
 * Brings a stored balance up to date.
 *
 * Returns the regenerated `points` and the `updatedAtMs` that must be stored with them. The
 * timestamp advances only by the whole points actually granted, so partial progress toward the
 * next point survives; at the ceiling it jumps to now, otherwise an idle account would build up
 * a backlog and refill instantly after spending.
 */
function regenerateLifePoints(storedPoints, updatedAtMs, nowMs, maxPoints) {
  const ceiling = Math.max(0, Math.floor(Number(maxPoints) || 0));
  // Не зажимаем вниз: потолок ограничивает пополнение, а не владение. Прежде здесь стоял
  // `Math.min`, и понижение потолка молча отбирало накопленное — игрок ничего не тратил, а очки
  // исчезали на первом же чтении. Теперь баланс выше потолка остаётся как есть и просто не растёт,
  // пока не опустится под него сам. То же правило действует и в клиентском `ChargeRegeneration`.
  const current = Math.max(0, Math.floor(Number(storedPoints) || 0));
  const since = Math.max(0, Math.floor(Number(updatedAtMs) || 0));
  const now = Math.max(0, Math.floor(Number(nowMs) || 0));

  if (current >= ceiling) return {points: current, updatedAtMs: Math.max(since, now)};
  if (now <= since) return {points: current, updatedAtMs: since};

  const gained = Math.floor((now - since) / LIFE_POINT_INTERVAL_MS);
  if (gained <= 0) return {points: current, updatedAtMs: since};

  const points = Math.min(ceiling, current + gained);
  if (points >= ceiling) return {points: ceiling, updatedAtMs: now};
  return {points, updatedAtMs: since + (points - current) * LIFE_POINT_INTERVAL_MS};
}

/**
 * Charges `cost` points against an up-to-date balance.
 *
 * `affordable` is false when the balance cannot cover the cost — the caller decides what that
 * means. The game is offline-first, so an attempt can legitimately arrive after the points were
 * already spent elsewhere; the server stays the authority and simply does not pay for it.
 */
function spendLifePoints(points, cost, maxPoints) {
  // `maxPoints` здесь больше ни на что не влияет: тратится то, что есть, а не то, что помещается.
  // Параметр оставлен, чтобы не переписывать десяток мест вызова ради одного удалённого аргумента.
  const current = Math.max(0, Math.floor(Number(points) || 0));
  const price = Math.max(0, Math.floor(Number(cost) || 0));
  if (current < price) return {affordable: false, points: current};
  return {affordable: true, points: current - price};
}

module.exports = {
  LIFE_POINTS_PER_HEART,
  HEART_REGEN_MS,
  LIFE_POINT_INTERVAL_MS,
  LESSON_ATTEMPT_LIFE_COST,
  maxLifePoints,
  regenerateLifePoints,
  spendLifePoints,
};
