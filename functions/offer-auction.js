"use strict";

/**
 * Ставка на аукционе предложений (история 4.1).
 *
 * Размещение на экране предложений продаётся за золото на открытом аукционе (CAP-11): ставка
 * списывает золото в той же транзакции, что и записывается, — списанного с баланса не существует
 * отдельно от записи о ставке, и наоборот. Повышение своей ставки возвращает прежнюю сумму и
 * списывает новой той же транзакцией. Пока аукцион идёт, стол ставок открыт: кто, сколько и когда.
 *
 * Аукцион всегда на следующий месяц: документ ключуется месяцем цели (`ГГГГ-ММ`), поэтому «ровно
 * один открытый аукцион в каждый момент» — свойство идентификатора, а не договорённость: второй
 * документ того же месяца создать невозможно. Первая ставка месяца создаёт документ сама —
 * инвариант исполняется без операторского бутстрапа; гонка двух первых ставок откатывает вторую
 * транзакцию целиком, и её повтор читает уже созданный аукцион (образец — guard 3.1).
 *
 * Creative объявления (картинка и ссылка) записывается со ставкой, но на открытый стол не едет:
 * непроверенная картинка — не публичная поверхность (показ и проверка — истории 4.2/4.3).
 *
 * Чистый модуль: тестируется без firebase-admin, Firestore приходит параметром.
 */

const {CURRENCY, LEDGER_PATH, REASON, ledgerEntry} = require("./currency-ledger");

const OFFER_AUCTIONS_COLLECTION = "offer_auctions";
const OFFER_BIDS_COLLECTION = "offer_bids";

/** Аукцион открыт: ставки принимаются до `endsAtMs` (закрывает его плановая работа 4.2). */
const OFFER_AUCTION_OPEN = "OPEN";
/** Месяц перевернулся: победитель записан, возвраты проигравшим идут порциями (4.2). */
const OFFER_AUCTION_SETTLING = "SETTLING";
/** Всё возвращено, аукцион закрыт: повторный прогон его не трогает (4.2). */
const OFFER_AUCTION_SETTLED = "SETTLED";

/**
 * Статус ставки — ключ повтора для возвратов: порция флипает его в той же транзакции, что и
 * начисляет золото, поэтому дважды возвращённая ставка требует дважды флипнуть один документ.
 */
const OFFER_BID_STATUS = {BID: "BID", WON: "WON", REFUNDED: "REFUNDED"};

/** Вместимость открытого стола: чтение ограничено, стол — верхушка, а не архив. */
const OFFER_BID_TABLE_LIMIT = 200;

/** Творческая часть объявления — строки ограниченной длины: хранилище не свалка. */
const OFFER_CREATIVE_MAX_LENGTH = 2048;

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }

function exactNonNegative(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) fail("INVALID_ARGUMENT", `${label} must be an exact nonnegative integer`);
  return value;
}

function creative(value, label) {
  if (typeof value !== "string" || value.trim() === "") fail("INVALID_ARGUMENT", `${label} is required`);
  if (value.length > OFFER_CREATIVE_MAX_LENGTH) fail("INVALID_ARGUMENT", `${label} is too long`);
  return value;
}

function storedGold(user) {
  const gold = Math.floor(Number(user && user.gold));
  return Number.isSafeInteger(gold) && gold >= 0 ? gold : 0;
}

/**
 * Календарный месяц по переданным серверным часам, всё в UTC.
 *
 * Аукцион месяца M открыт весь месяц M-1: `opensAtMs` — начало текущего месяца, `endsAtMs` —
 * начало месяца цели. «Месяц переворачивается» (4.2) — смена календарного месяца, никаких
 * катящихся суток: у месяцев нет часов.
 */
function openOfferAuction(nowMs) {
  const at = exactNonNegative(nowMs, "server time");
  const date = new Date(at);
  const year = date.getUTCFullYear();
  const month = date.getUTCMonth();
  const targetYear = month === 11 ? year + 1 : year;
  const targetMonth = (month + 1) % 12;
  const auctionId = `${targetYear}-${String(targetMonth + 1).padStart(2, "0")}`;
  return {auctionId, opensAtMs: Date.UTC(year, month, 1), endsAtMs: Date.UTC(targetYear, targetMonth, 1)};
}

function offerBidId(auctionId, uid) {
  return `${auctionId}_${uid}`;
}

/**
 * Источник строк книги называет ставку и номер повышения: `r0` — первая ставка, `r1` — первое
 * повышение. Возврат при повышении до `rn` называет сбрасываемый `r(n-1)` — он уникален ровно
 * один раз; повторная запись того же движения — переигранная транзакция, а не второе движение.
 */
function bidMovementId(auctionId, uid, raiseCount) {
  return `${offerBidId(auctionId, uid)}_r${exactNonNegative(raiseCount, "raiseCount")}`;
}

/**
 * Документ аукциона: сам факт «идёт открытый аукцион на следующий месяц», ничего больше.
 *
 * Итоги и победитель появляются на закрытии месяца (4.2); записи ставок живут своими документами.
 */
function offerAuctionRecord({auctionId, opensAtMs, endsAtMs}, nowMs) {
  return {
    auctionId,
    status: OFFER_AUCTION_OPEN,
    opensAtMs: exactNonNegative(opensAtMs, "opensAtMs"),
    endsAtMs: exactNonNegative(endsAtMs, "endsAtMs"),
    createdAtMs: exactNonNegative(nowMs, "server time"),
    updatedAtMs: exactNonNegative(nowMs, "server time"),
  };
}

/**
 * Чистое решение ставки: что запишет транзакция или каким именованным отказом ответит.
 *
 * @param auction документ аукциона или `null`, когда документа ещё нет — тогда он будет создан.
 * @param bid прежняя ставка этого игрока на этом аукционе или `null`.
 * @param gold записанный баланс игрока; отсутствующий документ читается нулём.
 * @returns kind: `BID` — транзакция пишет ставку и движение, `UNCHANGED` — повтор того же
 *   размера отвечает состоянием и не пишет ничего (NFR2).
 */
function planOfferBid({auction, bid, gold, amount, imageUrl, link, nowMs}) {
  exactNonNegative(nowMs, "server time");
  const bidAmount = exactNonNegative(amount, "bid amount");
  if (bidAmount <= 0) fail("INVALID_ARGUMENT", "bid amount must be positive");
  creative(imageUrl, "imageUrl");
  creative(link, "link");

  if (auction) {
    if (typeof auction.status !== "string") fail("INVALID_ARGUMENT", "auction status is corrupted");
    if (auction.status !== OFFER_AUCTION_OPEN) fail("FAILED_PRECONDITION", "The auction is not open");
    exactNonNegative(auction.endsAtMs, "auction.endsAtMs");
    exactNonNegative(auction.opensAtMs, "auction.opensAtMs");
    if (nowMs >= auction.endsAtMs) fail("FAILED_PRECONDITION", "The auction is closed");
    if (nowMs < auction.opensAtMs) fail("INVALID_ARGUMENT", "The auction has not opened yet");
  }

  const currentRaiseCount = bid ? exactNonNegative(bid.raiseCount, "stored bid raiseCount") : 0;
  const previous = bid ? exactNonNegative(bid.amount, "stored bid amount") : null;
  const delta = bidAmount - (previous === null ? 0 : previous);
  if (delta === 0) return {kind: "UNCHANGED", raiseCount: currentRaiseCount};
  if (delta < 0) fail("INVALID_ARGUMENT", "A bid can only be raised, not lowered");
  const goldAfter = exactNonNegative(gold, "gold") - delta;
  if (goldAfter < 0) fail("FAILED_PRECONDITION", "Not enough gold");
  // Номер движения ставки: первая ставка — r0, каждое повышение — следующий номер.
  return {kind: "BID", raiseCount: previous === null ? 0 : currentRaiseCount + 1, previous, bidAmount, goldAfter};
}

/**
 * Ставка: списание и запись одной транзакцией.
 *
 * Отказ любой ноги откатывает всё: ни списания без записи ставки, ни записи без списания. Повтор
 * той же суммы отвечает состоянием и не пишет ничего (NFR2). Отказы уходят именованными кодами —
 * клиент ветвится по коду провода, как у соседних денежных путей.
 *
 * @returns {{auctionId: string, amount: number, raiseCount: number, gold: number}}
 */
async function placeOfferBid({db, uid, amount, imageUrl, link, now}) {
  if (typeof uid !== "string" || !uid || uid.includes("/")) fail("INVALID_ARGUMENT", "Authenticated uid is required");
  const slot = openOfferAuction(now);
  const auctionRef = db.collection(OFFER_AUCTIONS_COLLECTION).doc(slot.auctionId);
  const bidRef = db.collection(OFFER_BIDS_COLLECTION).doc(offerBidId(slot.auctionId, uid));
  const userRef = db.collection("users").doc(uid);
  for (let pass = 0; pass < 2; pass++) {
    let response = null;
    try {
      await db.runTransaction(async (transaction) => {
        const [auctionSnapshot, bidSnapshot, userSnapshot] = await transaction.getAll(auctionRef, bidRef, userRef);
        const auction = auctionSnapshot.exists ? auctionSnapshot.data() || {} : null;
        const bid = bidSnapshot.exists ? bidSnapshot.data() || {} : null;
        const user = userSnapshot.exists ? userSnapshot.data() || {} : {};
        const plan = planOfferBid({auction, bid, gold: storedGold(user), amount, imageUrl, link, nowMs: now});
        if (plan.kind === "UNCHANGED") {
          response = {
            auctionId: slot.auctionId,
            amount: exactNonNegative(bid.amount, "stored bid amount"),
            raiseCount: plan.raiseCount,
            gold: storedGold(user),
          };
          return;
        }
        if (!auction) transaction.create(auctionRef, offerAuctionRecord(slot, now));
        transaction.set(bidRef, {
          auctionId: slot.auctionId,
          uid,
          amount: plan.bidAmount,
          imageUrl,
          link,
          createdAtMs: bid ? exactNonNegative(bid.createdAtMs, "stored bid createdAtMs") : now,
          updatedAtMs: now,
          raiseCount: plan.raiseCount,
          status: OFFER_BID_STATUS.BID,
        }, {merge: true});
        transaction.set(userRef, {gold: plan.goldAfter, updatedAtMs: now}, {merge: true});
        // Повышение — возврат прежней суммы и списание новой: две строки книги об одном движении
        // баланса; каждая называет свой номер повышения, и вместе они читаются как история ставки.
        const movements = [];
        if (plan.previous !== null) {
          movements.push({
            reason: REASON.OFFER_BID_REFUND,
            delta: plan.previous,
            sourceId: bidMovementId(slot.auctionId, uid, plan.raiseCount - 1),
            details: {auctionId: slot.auctionId, raiseTo: plan.bidAmount},
          });
        }
        movements.push({
          reason: REASON.OFFER_BID,
          delta: -plan.bidAmount,
          sourceId: bidMovementId(slot.auctionId, uid, plan.raiseCount),
          details: {auctionId: slot.auctionId, previousAmount: plan.previous === null ? 0 : plan.previous},
        });
        for (const movement of movements) {
          const row = ledgerEntry({...movement, uid, currency: CURRENCY.GOLD, atMs: now});
          if (row) transaction.set(db.doc(`${LEDGER_PATH}/${row.id}`), row, {merge: true});
        }
        response = {auctionId: slot.auctionId, amount: plan.bidAmount, raiseCount: plan.raiseCount, gold: plan.goldAfter};
      });
    } catch (error) {
      // Гонка первых ставок: аукцион успел создать кто-то другой. Второй проход читает его.
      if (pass === 0 && String(error && error.message).includes("ALREADY_EXISTS")) continue;
      throw error;
    }
    return response;
  }
  fail("UNAVAILABLE", "Offer bid did not settle; retry");
}

/**
 * Открытый стол ставок: кто, сколько и когда — по сумме, при равенстве по времени.
 *
 * Creative на столе нет: непроверенные картинка и ссылка — не публичная поверхность. Аукциона ещё
 * нет (ни одной ставки, ни одного закрытия) — стол отвечает `open: false`; чтение ничего не пишет.
 */
async function offerAuctionView({db, now, limit = OFFER_BID_TABLE_LIMIT}) {
  const slot = openOfferAuction(now);
  const auctionSnapshot = await db.collection(OFFER_AUCTIONS_COLLECTION).doc(slot.auctionId).get();
  if (!auctionSnapshot.exists) return {open: false, auctionId: slot.auctionId, endsAtMs: slot.endsAtMs, bids: []};
  // Стол — верхняя часть по сумме: предел обрезает хвост, а не топ. Порядок держит индекс
  // (firestore.indexes.json: auctionId ASC, amount DESC), дочитывание сортирует ничьи по времени.
  const query = db.collection(OFFER_BIDS_COLLECTION)
    .where("auctionId", "==", slot.auctionId)
    .orderBy("amount", "desc")
    .limit(limit);
  const bidsSnapshot = await query.get();
  const bids = bidsSnapshot.docs
    .map((doc) => doc.data() || {})
    .filter((bid) => typeof bid.uid === "string" && bid.uid !== "")
    .map((bid) => ({
      uid: bid.uid,
      amount: exactNonNegative(bid.amount, "stored bid amount"),
      atMs: exactNonNegative(bid.createdAtMs, "stored bid createdAtMs"),
    }))
    .sort((left, right) => right.amount - left.amount || left.atMs - right.atMs);
  const auction = auctionSnapshot.data() || {};
  return {
    open: auction.status === OFFER_AUCTION_OPEN && now < slot.endsAtMs,
    auctionId: slot.auctionId,
    endsAtMs: slot.endsAtMs,
    bids,
  };
}

module.exports = {
  OFFER_AUCTIONS_COLLECTION,
  OFFER_AUCTION_OPEN,
  OFFER_AUCTION_SETTLING,
  OFFER_AUCTION_SETTLED,
  OFFER_BIDS_COLLECTION,
  OFFER_BID_STATUS,
  OFFER_BID_TABLE_LIMIT,
  offerAuctionRecord,
  offerAuctionView,
  offerBidId,
  openOfferAuction,
  placeOfferBid,
  planOfferBid,
};
