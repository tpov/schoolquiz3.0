"use strict";

/**
 * Проверка объявления перед показом (история 4.3).
 *
 * Ставка выиграла — объявление ещё не на экране: закрытие месяца (4.2) кладёт его кандидатом
 * в `offers/pending`, а на показ уходит только `offers/current`, и перезаписать его может только
 * одобрение. Решение — модераторский callable: одобрение переводит кандидата в текущее место;
 * отказ возвращает ставку тем же языком, что и порции 4.2 (статус ставки BID/WON → REFUNDED в
 * транзакции начисления, строка книги с идентификатором ставки в качестве источника), и переводит
 * место к следующей проверенной ставке месяца — месту как таковому, не деньгам: размещение —
 * «not a reward» (paid-catalogue). Проверенных нет — кандидат просто исчезает, прошлый витрина
 * остаётся: экран не пустеет.
 *
 * Отклонённая ставка не может выиграть: закрытие месяца пропускает отклонённые и возвращённые
 * (pickWinner, offer-auction-settlement.js). Отказ ставки до закрытия возвращает её деньги сразу.
 *
 * Читающая поверхность (offersScreenView) отдаёт только проверенный current; на испорченном
 * состоянии она показывает пустоту, а не ошибку: экран, который не может утечь непроверенным
 * creative, честнее экрана, который падает.
 *
 * Чистый модуль: тестируется без firebase-admin, Firestore приходит параметром.
 */

const {CURRENCY, LEDGER_PATH, REASON, ledgerEntry} = require("./currency-ledger");
const {
  OFFER_BIDS_COLLECTION,
  OFFER_BID_STATUS,
  OFFER_BID_REVIEW,
} = require("./offer-auction");
const {
  OFFER_PLACEMENTS_COLLECTION,
  OFFER_PLACEMENT_CURRENT,
  OFFER_PLACEMENT_PENDING,
} = require("./offer-auction-settlement");

/** Решение модератора. */
const OFFER_REVIEW_DECISION = {APPROVED: "APPROVED", REJECTED: "REJECTED"};

/** Верхушка рейтинга для продвижения: место уходит верхним проверенным ставкам месяца. */
const OFFER_REVIEW_PROMO_READ = 50;

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }

function exactNonNegative(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) fail("INVALID_ARGUMENT", `${label} must be an exact nonnegative integer`);
  return value;
}

function placementRecord(pending, patch) {
  return {
    auctionId: pending.auctionId,
    bidId: pending.bidId,
    uid: pending.uid,
    imageUrl: pending.imageUrl,
    link: pending.link,
    ...patch,
  };
}

/**
 * Решение по одной ставке: одобрение, или отказ с возвратом и продвижением места.
 *
 * Guard — статус проверки: решение принимается ровно один раз, повтор отвечает
 * FAILED_PRECONDITION и не пишет ничего. Все чтения — до первой записи: ставка, кандидат,
 * верхушка рейтинга для продвижения и кошелёк отказника.
 *
 * @returns {{decision: string, bidId: string, refunded: boolean, promoted: ?string, promotedTo: ?string}}
 */
async function decideOfferAdReview({db, bidId, decision, now}) {
  if (typeof bidId !== "string" || !bidId || bidId.includes("/")) fail("INVALID_ARGUMENT", "bidId is required");
  exactNonNegative(now, "server time");
  if (decision !== OFFER_REVIEW_DECISION.APPROVED && decision !== OFFER_REVIEW_DECISION.REJECTED) {
    fail("INVALID_ARGUMENT", "decision must be APPROVED or REJECTED");
  }
  const bidsCollection = db.collection(OFFER_BIDS_COLLECTION);
  const placements = db.collection(OFFER_PLACEMENTS_COLLECTION);
  return db.runTransaction(async (transaction) => {
    // Чтения: ставка, кандидат, продвижение, кошелёк — до первой записи (порядок транзакции).
    const bidSnapshot = await transaction.get(bidsCollection.doc(bidId));
    if (!bidSnapshot.exists) fail("NOT_FOUND", "Bid not found");
    const bid = bidSnapshot.data() || {};
    if (bid.reviewStatus !== OFFER_BID_REVIEW.PENDING) fail("FAILED_PRECONDITION", "The bid has already been reviewed");
    if (typeof bid.auctionId !== "string" || bid.auctionId === "") fail("INVALID_ARGUMENT", "stored bid auctionId is corrupted");
    const uid = typeof bid.uid === "string" && bid.uid !== "" ? bid.uid : fail("INVALID_ARGUMENT", "stored bid uid is corrupted");
    const amount = exactNonNegative(bid.amount, "stored bid amount");

    const pendingRef = placements.doc(OFFER_PLACEMENT_PENDING);
    const pendingSnapshot = await transaction.get(pendingRef);
    const pending = pendingSnapshot.exists ? pendingSnapshot.data() || {} : {};
    const isPendingWinner = pending.bidId === bidId;
    // Creative кандидата едет на экран целиком из этого документа: испорченный кандидат — громкий
    // отказ до первой записи, а не мусор на витрине (чтения — до записей).
    if (isPendingWinner) {
      for (const field of ["uid", "imageUrl", "link", "auctionId"]) {
        if (typeof pending[field] !== "string" || pending[field] === "") {
          fail("INVALID_ARGUMENT", `pending candidate ${field} is corrupted`);
        }
      }
    }

    let promotion = null;
    let rejectorGold = 0;
    if (decision === OFFER_REVIEW_DECISION.REJECTED) {
      if (isPendingWinner && typeof pending.auctionId === "string") {
        // Место — следующей проверенной ставке месяца: верхушка рейтинга, первый со статусом
        // «проверено» (деньги проигравших уже возвращены 4.2 — место ими не платится).
        const topQuery = bidsCollection
          .where("auctionId", "==", pending.auctionId)
          .orderBy("amount", "desc")
          .limit(OFFER_REVIEW_PROMO_READ);
        const topSnapshot = await transaction.get(topQuery);
        for (const doc of topSnapshot.docs) {
          if (doc.id === bidId) continue;
          const candidate = doc.data() || {};
          if (candidate.reviewStatus !== OFFER_BID_REVIEW.APPROVED) continue;
          if (typeof candidate.uid !== "string" || candidate.uid === "") continue;
          // Испорченный кандидат продвижения пропускается, а не роняет решение: место занимает
          // следующая проверенная. Авторитетный кандидат (pending) выше — громкий отказ.
          if (typeof candidate.imageUrl !== "string" || candidate.imageUrl === "" ||
              typeof candidate.link !== "string" || candidate.link === "") continue;
          promotion = {
            id: doc.id,
            uid: candidate.uid,
            imageUrl: candidate.imageUrl,
            link: candidate.link,
          };
          break;
        }
      }
      // Отказ — возврат языком 4.2: из BID (отказ до закрытия) или WON (победа отменена
      // проверкой); из REFUNDED не бывает — guard по статусу проверки отсёк повтор.
      if (bid.status === OFFER_BID_STATUS.BID || bid.status === OFFER_BID_STATUS.WON) {
        const userSnapshot = await transaction.get(db.collection("users").doc(uid));
        rejectorGold = userSnapshot.exists ? Math.max(0, Math.floor(Number((userSnapshot.data() || {}).gold) || 0)) : 0;
      }
    }

    if (decision === OFFER_REVIEW_DECISION.APPROVED) {
      transaction.set(bidsCollection.doc(bidId),
        {reviewStatus: OFFER_BID_REVIEW.APPROVED, reviewedAtMs: now, updatedAtMs: now}, {merge: true});
      if (isPendingWinner) {
        transaction.set(placements.doc(OFFER_PLACEMENT_CURRENT),
          placementRecord(pending, {reviewStatus: OFFER_BID_REVIEW.APPROVED, approvedAtMs: now}));
        transaction.delete(pendingRef);
        return {decision, bidId, refunded: false, promoted: bidId, promotedTo: OFFER_PLACEMENT_CURRENT};
      }
      return {decision, bidId, refunded: false, promoted: null, promotedTo: null};
    }

    const refund = bid.status === OFFER_BID_STATUS.BID || bid.status === OFFER_BID_STATUS.WON;
    const bidPatch = {
      reviewStatus: OFFER_BID_REVIEW.REJECTED,
      reviewedAtMs: now,
      updatedAtMs: now,
      ...(refund ? {status: OFFER_BID_STATUS.REFUNDED, refundedAtMs: now} : {}),
    };
    transaction.set(bidsCollection.doc(bidId), bidPatch, {merge: true});
    if (refund) {
      transaction.set(db.collection("users").doc(uid),
        {gold: rejectorGold + amount, updatedAtMs: now}, {merge: true});
      const row = ledgerEntry({
        uid,
        currency: CURRENCY.GOLD,
        delta: amount,
        reason: REASON.OFFER_BID_REFUND,
        sourceId: bidId,
        atMs: now,
        details: {auctionId: bid.auctionId, rejected: true},
      });
      if (row) transaction.set(db.doc(`${LEDGER_PATH}/${row.id}`), row, {merge: true});
    }
    if (isPendingWinner) {
      if (promotion) {
        transaction.set(placements.doc(OFFER_PLACEMENT_CURRENT), {
          auctionId: pending.auctionId,
          bidId: promotion.id,
          uid: promotion.uid,
          imageUrl: promotion.imageUrl,
          link: promotion.link,
          reviewStatus: OFFER_BID_REVIEW.APPROVED,
          promotedAtMs: now,
        });
      }
      transaction.delete(pendingRef);
    }
    return {decision, bidId, refunded: refund, promoted: promotion ? promotion.id : null, promotedTo: promotion ? OFFER_PLACEMENT_CURRENT : null};
  });
}

/**
 * Экран предложений: только проверенное размещение, ничего больше.
 *
 * Кандидат и статусы проверки здесь не видны никому. Испорченный current показом не становится:
 * экран отвечает пустотой, а не ошибкой и не чужой картинкой.
 */
async function offersScreenView({db}) {
  const currentSnapshot = await db.collection(OFFER_PLACEMENTS_COLLECTION).doc(OFFER_PLACEMENT_CURRENT).get();
  if (!currentSnapshot.exists) return {placement: null};
  const placement = currentSnapshot.data() || {};
  if (placement.reviewStatus !== OFFER_BID_REVIEW.APPROVED) return {placement: null};
  return {
    placement: {
      auctionId: placement.auctionId,
      bidId: placement.bidId,
      uid: placement.uid,
      imageUrl: placement.imageUrl,
      link: placement.link,
      approvedAtMs: placement.approvedAtMs === undefined ? placement.promotedAtMs : placement.approvedAtMs,
    },
  };
}

module.exports = {
  OFFER_PLACEMENT_CURRENT,
  OFFER_PLACEMENT_PENDING,
  OFFER_REVIEW_DECISION,
  OFFER_REVIEW_PROMO_READ,
  decideOfferAdReview,
  offersScreenView,
  placementRecord,
};
