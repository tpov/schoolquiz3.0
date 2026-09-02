"use strict";

/**
 * Коробки: серия ежедневных визитов, начисление по прошедшим дням и проверка того, что устройство
 * насчитало без связи (CAP-12).
 *
 * Правила — из `box-economy.md`, и только оттуда: визит продвигает серию не чаще раза в сутки; с
 * десятого дня подряд каждый визит даёт коробку; потолок — две коробки в день (одна от серии, одна
 * от рекламы). Здесь — механика «офлайн копится, онлайн открывается»; ни одной ставки своей.
 *
 * Прежний `advanceGiftBoxState` жил только внутри открытия коробки и двигал серию ровно на один
 * день за вызов, сколько бы дней ни прошло: пять дней отсутствия и одно открытие считались одним
 * днём. Слово «подряд» он не знал вовсе — серия не рвалась никогда. Здесь оба направления
 * поправлены: прошедшие дни считаются, а пропущенный день серию обрывает.
 *
 * Открытие остаётся серверным: содержимое решает и выдаёт сервер, и без связи открыть нельзя.
 * Чистый модуль: без firebase-admin.
 */

const DAY_MS = 24 * 60 * 60 * 1000;

/** С какого дня серии визит даёт коробку. `GIFT_BOX_STREAK_TARGET_DAYS` в `box-economy.md`. */
const STREAK_TARGET_DAYS = 10;

/** Больше стольких коробок в день честной игрой не бывает: одна от серии, одна от рекламы. */
const BOXES_PER_DAY_CEILING = 2;

/** Сколько из дневного потолка может дать серия. Остальное — реклама, и это не её модуль. */
const STREAK_BOXES_PER_DAY = 1;

function nonNegativeInt(value) {
  const number = Math.floor(Number(value));
  return Number.isFinite(number) && number > 0 ? number : 0;
}

/**
 * Состояние коробок, каким его хранит документ игрока.
 *
 * `nextBoxAtMs` — момент, с которого следующий визит засчитывается; ноль — серии ещё не было.
 * Старые имена полей (`countBox`, `countDayBox`, `timeLastOpenBox`) читаются как запасные: они
 * остались от прежнего приложения и в живых документах ещё встречаются.
 */
function readBoxState(user) {
  const source = user && typeof user === "object" ? user : {};
  return {
    boxCount: nonNegativeInt(source.boxCount !== undefined ? source.boxCount : source.countBox),
    boxStreakDays: nonNegativeInt(source.boxStreakDays !== undefined ? source.boxStreakDays : source.countDayBox),
    nextBoxAtMs: nonNegativeInt(source.nextBoxAtMs !== undefined ? source.nextBoxAtMs : source.timeLastOpenBox),
  };
}

/**
 * Продвигает серию по серверным часам — на визите, который сервер видит сам.
 *
 * Сутки считаются от `nextBoxAtMs`: он ставится на 24 часа после засчитанного визита, поэтому
 * визит до него — повтор в тех же сутках и ничего не меняет. Визит в течение следующих суток
 * продлевает серию на день. Визит позже — день пропущен, и серия начинается заново с первого:
 * «подряд» значит подряд.
 *
 * Коробка выдаётся за визит с десятого дня серии, и только одна: сколько бы дней ни прошло, за
 * один визит не бывает нескольких коробок — дни без визита не «копятся», они серию и обрывают.
 */
function advanceStreak(user, nowMs) {
  const state = readBoxState(user);
  const now = nonNegativeInt(nowMs);
  if (state.nextBoxAtMs === 0) {
    // Первый визит вообще: серия открывается, коробка — не раньше десятого дня.
    return {boxCount: state.boxCount, boxStreakDays: 1, nextBoxAtMs: now + DAY_MS, grantedBoxes: 0};
  }
  if (now < state.nextBoxAtMs) {
    return {...state, grantedBoxes: 0};
  }
  const missed = now >= state.nextBoxAtMs + DAY_MS;
  const streak = missed ? 1 : state.boxStreakDays + 1;
  const granted = streak >= STREAK_TARGET_DAYS ? STREAK_BOXES_PER_DAY : 0;
  return {
    boxCount: state.boxCount + granted,
    boxStreakDays: streak,
    nextBoxAtMs: now + DAY_MS,
    grantedBoxes: granted,
  };
}

/**
 * Что устройство могло честно насчитать без связи — и что из заявленного принимается.
 *
 * Без связи сервер визитов не видит; он видит только время, которое прошло с последнего слова, и
 * ограничивает заявку им: серия не длиннее, чем было плюс прошедшие сутки, коробок не больше, чем
 * дней серии от десятого, и не больше одной в день от серии. Всё сверх этого — излишек: не
 * начисляется и записывается, как любой перерасход (CAP-7). Слишком скромная заявка принимается
 * как есть — ниже потолка врать незачем, а самому насчитывать за устройство визиты, которых оно не
 * заявило, нельзя.
 *
 * @param stored состояние по последнему слову сервера
 * @param claimed то, что устройство насчитало: `boxStreakDays`, `boxesEarned`
 * @param nowMs серверные часы
 */
function boxAccrualVerdict({stored, claimed, nowMs}) {
  const base = readBoxState(stored);
  const now = nonNegativeInt(nowMs);
  const claimedStreak = nonNegativeInt(claimed && claimed.boxStreakDays);
  const claimedBoxes = nonNegativeInt(claimed && claimed.boxesEarned);

  // Сколько суток могло начаться с последнего засчитанного визита. Без серии — с нуля.
  const lastVisitMs = base.nextBoxAtMs > 0 ? base.nextBoxAtMs - DAY_MS : 0;
  // Без единого слова сервера серию не с чем сверить: принимается один день — первый визит, — а
  // всё, что устройство насчитало сверх него, остаётся его словом против отсутствующего.
  const elapsedDays = base.nextBoxAtMs > 0 ? Math.max(0, Math.floor((now - lastVisitMs) / DAY_MS)) : 1;
  const streakCeiling = base.boxStreakDays + elapsedDays;
  const allowedStreak = Math.min(claimedStreak, streakCeiling);

  // Коробка — за каждый день серии начиная с десятого, по одной в день, и только за дни, которые
  // устройство заявило как пройденные.
  const boxDays = Math.max(0, allowedStreak - Math.max(base.boxStreakDays, STREAK_TARGET_DAYS - 1));
  const boxCeiling = Math.min(boxDays, elapsedDays * STREAK_BOXES_PER_DAY);
  const allowedBoxes = Math.min(claimedBoxes, boxCeiling);

  return {
    elapsedDays,
    streakCeiling,
    allowedStreak,
    boxCeiling,
    allowedBoxes,
    surplusStreak: Math.max(0, claimedStreak - allowedStreak),
    surplusBoxes: Math.max(0, claimedBoxes - allowedBoxes),
    next: {
      boxCount: base.boxCount + allowedBoxes,
      boxStreakDays: allowedStreak > 0 ? allowedStreak : base.boxStreakDays,
      // Следующие сутки — от последнего заявленного дня, а не от момента синхронизации: иначе
      // синхронизация вечером сдвигала бы всю серию на вечер.
      nextBoxAtMs: allowedStreak > base.boxStreakDays ? lastVisitMs + (allowedStreak - base.boxStreakDays) * DAY_MS + DAY_MS : base.nextBoxAtMs,
    },
  };
}

/**
 * Запись о завышенной заявке — обоснование внутри, по образцу записи о перерасходе зарядов.
 */
function boxOverclaimRecord({uid, stored, claimed, verdict, recordedAtMs}) {
  const base = readBoxState(stored);
  return {
    id: `${String(uid || "")}_BOXES_${nonNegativeInt(recordedAtMs)}`,
    uid: String(uid || ""),
    storedStreakDays: base.boxStreakDays,
    storedNextBoxAtMs: base.nextBoxAtMs,
    storedBoxCount: base.boxCount,
    elapsedDays: verdict.elapsedDays,
    claimedStreakDays: nonNegativeInt(claimed && claimed.boxStreakDays),
    claimedBoxes: nonNegativeInt(claimed && claimed.boxesEarned),
    allowedStreak: verdict.allowedStreak,
    allowedBoxes: verdict.allowedBoxes,
    surplusStreak: verdict.surplusStreak,
    surplusBoxes: verdict.surplusBoxes,
    reason:
      `Заявлено ${nonNegativeInt(claimed && claimed.boxesEarned)} коробок и серия ${nonNegativeInt(claimed && claimed.boxStreakDays)} дней, ` +
      `а с последнего слова сервера прошло ${verdict.elapsedDays} суток: серия не длиннее ${verdict.streakCeiling}, ` +
      `коробок не больше ${verdict.boxCeiling}; излишек ${verdict.surplusBoxes} коробок не начислен.`,
    recordedAtMs: nonNegativeInt(recordedAtMs),
  };
}

module.exports = {
  BOXES_PER_DAY_CEILING,
  DAY_MS,
  STREAK_BOXES_PER_DAY,
  STREAK_TARGET_DAYS,
  advanceStreak,
  boxAccrualVerdict,
  boxOverclaimRecord,
  readBoxState,
};
