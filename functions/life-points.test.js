"use strict";

const assert = require("assert");
const {
  LIFE_POINTS_PER_HEART,
  HEART_REGEN_MS,
  LIFE_POINT_INTERVAL_MS,
  LESSON_ATTEMPT_LIFE_COST,
  maxLifePoints,
  regenerateLifePoints,
  spendLifePoints,
} = require("./life-points");

const MINUTE = 60 * 1000;

function testConstantsAgree() {
  assert.strictEqual(LIFE_POINTS_PER_HEART, 100);
  assert.strictEqual(HEART_REGEN_MS, 60 * MINUTE);
  // 100 points per hour → one point every 36 seconds.
  assert.strictEqual(LIFE_POINT_INTERVAL_MS, 36 * 1000);
  assert.strictEqual(LESSON_ATTEMPT_LIFE_COST, 33);
}

function testCeilingFollowsOwnedHearts() {
  assert.strictEqual(maxLifePoints(0), 0);
  assert.strictEqual(maxLifePoints(1), 100);
  assert.strictEqual(maxLifePoints(5), 500);
}

function testRegenerationGrantsWholePointsOnly() {
  // 35 s is not enough for a point yet.
  assert.deepStrictEqual(
    regenerateLifePoints(0, 0, 35 * 1000, 500),
    {points: 0, updatedAtMs: 0},
  );
  // 36 s is exactly one point.
  assert.strictEqual(regenerateLifePoints(0, 0, LIFE_POINT_INTERVAL_MS, 500).points, 1);
  // One hour is a full heart.
  assert.strictEqual(regenerateLifePoints(0, 0, HEART_REGEN_MS, 500).points, 100);
}

function testPartialProgressIsNotLost() {
  // 100 s = 2 points (72 s) with 28 s left over; the timestamp must keep that remainder.
  const result = regenerateLifePoints(0, 0, 100 * 1000, 500);
  assert.strictEqual(result.points, 2);
  assert.strictEqual(result.updatedAtMs, 2 * LIFE_POINT_INTERVAL_MS);

  // Continuing from there, only 8 more seconds are needed for the third point.
  const next = regenerateLifePoints(result.points, result.updatedAtMs, 108 * 1000, 500);
  assert.strictEqual(next.points, 3);
}

function testCeilingStopsAccrualAndDoesNotBankTime() {
  // Already full: the timestamp jumps to now, so a long absence cannot be cashed in later.
  const full = regenerateLifePoints(500, 0, 10 * HEART_REGEN_MS, 500);
  assert.deepStrictEqual(full, {points: 500, updatedAtMs: 10 * HEART_REGEN_MS});

  // Reaching the ceiling mid-way behaves the same.
  const reached = regenerateLifePoints(450, 0, 10 * HEART_REGEN_MS, 500);
  assert.strictEqual(reached.points, 500);
  assert.strictEqual(reached.updatedAtMs, 10 * HEART_REGEN_MS);

  // So spending right after a long absence leaves one attempt's worth, not a full tank.
  const afterSpend = spendLifePoints(full.points, LESSON_ATTEMPT_LIFE_COST, 500);
  assert.strictEqual(afterSpend.points, 500 - 33);
}

function testClockSkewDoesNotGrantPoints() {
  // A timestamp in the future must not regenerate anything.
  assert.deepStrictEqual(
    regenerateLifePoints(10, 5_000, 1_000, 500),
    {points: 10, updatedAtMs: 5_000},
  );
}

function testStoredValueAboveCeilingIsKeptRatherThanConfiscated() {
  // Прежде здесь стояло обратное утверждение — «потерянный слот не должен оставить баланс выше
  // нового потолка», — и это было конфискацией: игрок ничего не тратил, а очки исчезали на первом
  // же чтении. Потолок ограничивает пополнение, а не владение.
  //
  // Правило названо в спеке зарядов и действует по обе стороны: та же арифметика в клиентском
  // `ChargeRegeneration`. Пока таблица настроек была зашита в код, разницы не было видно; она
  // затем и делается серверной, чтобы потолки двигались.
  assert.strictEqual(regenerateLifePoints(500, 0, 1_000, 100).points, 500);
  // И расти при этом не начинает.
  assert.strictEqual(regenerateLifePoints(500, 0, 10_000_000, 100).points, 500);
}

function testSpending() {
  assert.deepStrictEqual(spendLifePoints(100, 33, 500), {affordable: true, points: 67});
  // Exactly enough is still affordable.
  assert.deepStrictEqual(spendLifePoints(33, 33, 500), {affordable: true, points: 0});
  // One short is not, and the balance is left untouched.
  assert.deepStrictEqual(spendLifePoints(32, 33, 500), {affordable: false, points: 32});
}

function testFullTankAllowsFifteenAttempts() {
  // 500 / 33 = 15 attempts, then the sixteenth is refused.
  let points = 500;
  let played = 0;
  for (;;) {
    const result = spendLifePoints(points, LESSON_ATTEMPT_LIFE_COST, 500);
    if (!result.affordable) break;
    points = result.points;
    played += 1;
  }
  assert.strictEqual(played, 15);
  assert.strictEqual(points, 500 - 15 * 33);
}

testConstantsAgree();
testCeilingFollowsOwnedHearts();
testRegenerationGrantsWholePointsOnly();
testPartialProgressIsNotLost();
testCeilingStopsAccrualAndDoesNotBankTime();
testClockSkewDoesNotGrantPoints();
testStoredValueAboveCeilingIsKeptRatherThanConfiscated();
testSpending();
testFullTankAllowsFifteenAttempts();

console.log("life-points tests passed");
