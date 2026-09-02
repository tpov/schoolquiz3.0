"use strict";

const assert = require("assert");
const {
  DAY_MS,
  STREAK_TARGET_DAYS,
  advanceStreak,
  boxAccrualVerdict,
  boxOverclaimRecord,
  readBoxState,
} = require("./gift-boxes");

const T0 = 1_700_000_000_000;
const HOUR = 60 * 60 * 1000;

function afterDays(user, days, startMs) {
  // Визит раз в сутки, `days` дней подряд.
  let state = user;
  let clock = startMs;
  for (let i = 0; i < days; i += 1) {
    state = advanceStreak(state, clock);
    clock += DAY_MS;
  }
  return state;
}

function testTheFirstVisitOpensTheStreakWithoutABox() {
  const state = advanceStreak({}, T0);
  assert.strictEqual(state.boxStreakDays, 1);
  assert.strictEqual(state.grantedBoxes, 0);
  assert.strictEqual(state.nextBoxAtMs, T0 + DAY_MS);
}

function testASecondVisitInTheSameDayChangesNothing() {
  const first = advanceStreak({}, T0);
  const again = advanceStreak(first, T0 + 5 * HOUR);
  assert.strictEqual(again.boxStreakDays, 1);
  assert.strictEqual(again.nextBoxAtMs, first.nextBoxAtMs, "сутки считаются от засчитанного визита");
}

function testTheTenthConsecutiveDayGrantsABoxAndEveryDayAfterToo() {
  const nine = afterDays({}, STREAK_TARGET_DAYS - 1, T0);
  assert.strictEqual(nine.boxCount, 0, "девять дней — ещё ничего");
  const ten = advanceStreak(nine, T0 + (STREAK_TARGET_DAYS - 1) * DAY_MS);
  assert.strictEqual(ten.boxStreakDays, 10);
  assert.strictEqual(ten.boxCount, 1);
  const eleven = advanceStreak(ten, T0 + STREAK_TARGET_DAYS * DAY_MS);
  assert.strictEqual(eleven.boxCount, 2);
}

function testAMissedDayBreaksTheStreak() {
  // «Подряд» значит подряд: прежний код серию не рвал никогда.
  const twelve = afterDays({}, 12, T0);
  const late = advanceStreak(twelve, T0 + 12 * DAY_MS + DAY_MS + HOUR);
  assert.strictEqual(late.boxStreakDays, 1);
  assert.strictEqual(late.grantedBoxes, 0);
  assert.strictEqual(late.boxCount, twelve.boxCount, "накопленные коробки не сгорают");
}

function testDaysAwayDoNotPileUpIntoBoxes() {
  // Пять дней отсутствия и одно открытие — это один визит после обрыва, а не пять начислений.
  const twelve = afterDays({}, 12, T0);
  const back = advanceStreak(twelve, T0 + 12 * DAY_MS + 5 * DAY_MS);
  assert.strictEqual(back.grantedBoxes, 0);
  assert.strictEqual(back.boxStreakDays, 1);
}

function testAnHonestOfflineWeekIsAcceptedInFull() {
  // Устройство неделю без связи открывалось каждый день с одиннадцатого дня серии: семь коробок.
  const stored = afterDays({}, 10, T0); // серия 10, одна коробка
  const lastVisit = T0 + 9 * DAY_MS;
  const verdict = boxAccrualVerdict({
    stored,
    claimed: {boxStreakDays: 17, boxesEarned: 7},
    nowMs: lastVisit + 7 * DAY_MS + HOUR,
  });
  assert.strictEqual(verdict.elapsedDays, 7);
  assert.strictEqual(verdict.allowedStreak, 17);
  assert.strictEqual(verdict.allowedBoxes, 7);
  assert.strictEqual(verdict.surplusBoxes, 0);
  assert.strictEqual(verdict.next.boxCount, stored.boxCount + 7);
  assert.strictEqual(verdict.next.nextBoxAtMs, lastVisit + 8 * DAY_MS, "сутки — от последнего заявленного дня");
}

function testAFabricatedCountIsCappedByTheClockAndRecorded() {
  // Устройство насчитало сорок коробок за три дня: даётся то, что могло быть, остальное — запись.
  const stored = afterDays({}, 10, T0);
  const lastVisit = T0 + 9 * DAY_MS;
  const verdict = boxAccrualVerdict({
    stored,
    claimed: {boxStreakDays: 50, boxesEarned: 40},
    nowMs: lastVisit + 3 * DAY_MS + HOUR,
  });
  assert.strictEqual(verdict.allowedStreak, 13);
  assert.strictEqual(verdict.allowedBoxes, 3);
  assert.strictEqual(verdict.surplusBoxes, 37);
  const record = boxOverclaimRecord({uid: "u1", stored, claimed: {boxStreakDays: 50, boxesEarned: 40}, verdict, recordedAtMs: 5});
  assert.ok(record.reason.includes("излишек 37"), record.reason);
  assert.strictEqual(record.elapsedDays, 3);
}

function testBoxesNeedTheStreakToReachTenFirst() {
  // Три дня без связи с серии в пять — серия восемь, коробок ноль, сколько бы устройство ни заявило.
  const stored = afterDays({}, 5, T0);
  const verdict = boxAccrualVerdict({
    stored,
    claimed: {boxStreakDays: 8, boxesEarned: 3},
    nowMs: T0 + 4 * DAY_MS + 3 * DAY_MS + HOUR,
  });
  assert.strictEqual(verdict.allowedStreak, 8);
  assert.strictEqual(verdict.allowedBoxes, 0);
  assert.strictEqual(verdict.surplusBoxes, 3);
}

function testAModestClaimIsNotToppedUp() {
  // Ниже потолка врать незачем — и досчитывать за устройство визиты, которых оно не заявило, нельзя.
  const stored = afterDays({}, 10, T0);
  const verdict = boxAccrualVerdict({
    stored,
    claimed: {boxStreakDays: 11, boxesEarned: 1},
    nowMs: T0 + 9 * DAY_MS + 5 * DAY_MS,
  });
  assert.strictEqual(verdict.allowedStreak, 11);
  assert.strictEqual(verdict.allowedBoxes, 1);
}

function testWithoutAServerWordOnlyTheFirstDayIsTaken() {
  const verdict = boxAccrualVerdict({stored: {}, claimed: {boxStreakDays: 30, boxesEarned: 20}, nowMs: T0});
  assert.strictEqual(verdict.allowedStreak, 1);
  assert.strictEqual(verdict.allowedBoxes, 0);
}

function testLegacyFieldNamesAreStillRead() {
  const state = readBoxState({countBox: 2, countDayBox: 4, timeLastOpenBox: T0});
  assert.deepStrictEqual(state, {boxCount: 2, boxStreakDays: 4, nextBoxAtMs: T0});
}

testTheFirstVisitOpensTheStreakWithoutABox();
testASecondVisitInTheSameDayChangesNothing();
testTheTenthConsecutiveDayGrantsABoxAndEveryDayAfterToo();
testAMissedDayBreaksTheStreak();
testDaysAwayDoNotPileUpIntoBoxes();
testAnHonestOfflineWeekIsAcceptedInFull();
testAFabricatedCountIsCappedByTheClockAndRecorded();
testBoxesNeedTheStreakToReachTenFirst();
testAModestClaimIsNotToppedUp();
testWithoutAServerWordOnlyTheFirstDayIsTaken();
testLegacyFieldNamesAreStillRead();

console.log("gift-boxes.test.js OK");
