"use strict";

/**
 * Переворот месяца аукциона предложений (история 4.2).
 *
 * Месяц закончился — плановая работа закрывает аукцион ровно один раз: верхняя ставка становится
 * победителем и занимает место (документ размещения `offers/current`), её золото сгорает — оно
 * списано ставкой в 4.1 и просто не возвращается; проигравшим золото возвращается полностью,
 * порциями ограниченного размера; в тот же момент открывается аукцион следующего месяца.
 *
 * Ключи повтора — сами статусы: транзакция закрытия флипает аукцион только из OPEN (повтор
 * читает SETTLING/SETTLED и уходит), каждая порция возврата флипает статус конкретной ставки
 * в той же транзакции, что и начисляет золото (двойной возврат требует дважды флипнуть один
 * документ — невозможно), последний пустой запрос и флип в SETTLED закрывают историю. Прогон,
 * упавший между порциями, продолжает с того же места: недовозвращённые ставки всё ещё BID.
 *
 * Место не пустует структурно: размещение перезаписывается только закрытием месяца с победителем;
 * месяц без ставок оставляет прошлую витрину на месте.
 *
 * Чистый модуль: тестируется без firebase-admin, Firestore приходит параметром.
 */

const {CURRENCY, LEDGER_PATH, REASON, ledgerEntry} = require("./currency-ledger");
const {
  OFFER_AUCTIONS_COLLECTION,
  OFFER_AUCTION_OPEN,
  OFFER_AUCTION_SETTLING,
  OFFER_AUCTION_SETTLED,
  OFFER_BIDS_COLLECTION,
  OFFER_BID_STATUS,
} = require("./offer-auction");

/** Куда ложится объявление победителя; читает его будущая витрина, правилам закрыто. */
const OFFER_PLACEMENTS_COLLECTION = "offers";
const OFFER_PLACEMENT_DOC = "current";

/**
 * Вместимость транзакции: порция возврата пишет флип, строку книги и баланс на каждую ставку —
 * сто ставок дают ~300 записей против пятисот допустимых. Граница модуля, не таблицы экономики.
 */
const OFFER_SETTLE_BATCH = 100;

/** Верхушка для победителя: больше двадцати ставок с одной верхней суммой у месяца нет. */
const OFFER_SETTLE_TOP_READ = 20;

/** Сколько аукционов переворачивает один прогон: расписание догонит остальное следующим тиком. */
const OFFER_SETTLE_SWEEP_LIMIT = 10;

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }

function exactNonNegative(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) fail("INVALID_ARGUMENT", `${label} must be an exact nonnegative integer`);
  return value;
}

function storedGold(user) {
  const gold = Math.floor(Number(user && user.gold));
  return Number.isSafeInteger(gold) && gold >= 0 ? gold : 0;
}

/**
 * Месяц, следующий за месяцем цели закрытого аукциона.
 *
 * Границы выводятся из ключа-месяца, а не из часов: окно следующего аукциона — ровно то, каким
 * его построил бы вызов 4.1 в первый день месяца-цели этого аукциона.
 */
function followingMonthSlot(auctionId, endsAtMs) {
  const match = /^(\d{4})-(\d{2})$/.exec(String(auctionId || ""));
  if (!match) fail("INVALID_ARGUMENT", "auction id is not a month key");
  const year = Number(match[1]);
  const month = Number(match[2]);
  if (month < 1 || month > 12) fail("INVALID_ARGUMENT", "auction id is not a month key");
  const nextYear = month === 12 ? year + 1 : year;
  const nextMonth = month === 12 ? 1 : month + 1;
  return {
    auctionId: `${nextYear}-${String(nextMonth).padStart(2, "0")}`,
    opensAtMs: exactNonNegative(endsAtMs, "auction.endsAtMs"),
    endsAtMs: Date.UTC(nextYear, nextMonth, 1),
  };
}

/** Победитель: верхняя сумма, при равенстве — пришедшая раньше. Пустой месяц — `null`. */
function pickWinner(docs) {
  let winner = null;
  for (const doc of docs) {
    const bid = doc.data() || {};
    if (typeof bid.uid !== "string" || bid.uid === "") continue;
    const candidate = {
      id: doc.id,
      uid: bid.uid,
      amount: exactNonNegative(bid.amount, "stored bid amount"),
      createdAtMs: exactNonNegative(bid.createdAtMs, "stored bid createdAtMs"),
      imageUrl: bid.imageUrl,
      link: bid.link,
    };
    if (!winner ||
        candidate.amount > winner.amount ||
        (candidate.amount === winner.amount && candidate.createdAtMs < winner.createdAtMs)) {
      winner = candidate;
    }
  }
  return winner;
}

/**
 * Закрытие одного аукциона: guard по статусу, победитель, размещение, следующий месяц.
 *
 * Транзакция выполняет работу только на выходе из OPEN: повторный прогон читает SETTLING или
 * SETTLED и не пишет ничего. Победителю статус ставки переводится в WON — его золото сгорает
 * тем, что возврата не будет; размещение перезаписывается заменой, чтобы от прошлого месяца
 * не оставалось полей.
 *
 * @returns {"closed" | "not-open" | "not-due" | "missing"} — что сделал этот прогон.
 */
async function closeOfferAuction({db, auctionId, now}) {
  exactNonNegative(now, "server time");
  const outcome = await db.runTransaction(async (transaction) => {
    const auctionRef = db.collection(OFFER_AUCTIONS_COLLECTION).doc(auctionId);
    const auctionSnapshot = await transaction.get(auctionRef);
    if (!auctionSnapshot.exists) return "missing";
    const auction = auctionSnapshot.data() || {};
    if (auction.status !== OFFER_AUCTION_OPEN) return "not-open";
    if (now < exactNonNegative(auction.endsAtMs, "auction.endsAtMs")) return "not-due";
    exactNonNegative(auction.opensAtMs, "auction.opensAtMs");

    const topQuery = db.collection(OFFER_BIDS_COLLECTION)
      .where("auctionId", "==", auctionId)
      .orderBy("amount", "desc")
      .limit(OFFER_SETTLE_TOP_READ);
    const topSnapshot = await transaction.get(topQuery);
    const winner = pickWinner(topSnapshot.docs);
    // В тот же момент открывается аукцион следующего месяца; созданный ставками 4.1 не трогается.
    // Все чтения — до первой записи: порядок транзакции Firestore.
    const next = followingMonthSlot(auctionId, auction.endsAtMs);
    const nextRef = db.collection(OFFER_AUCTIONS_COLLECTION).doc(next.auctionId);
    const nextSnapshot = await transaction.get(nextRef);
    if (winner) {
      transaction.set(db.collection(OFFER_BIDS_COLLECTION).doc(winner.id),
        {status: OFFER_BID_STATUS.WON, updatedAtMs: now}, {merge: true});
      transaction.set(db.collection(OFFER_PLACEMENTS_COLLECTION).doc(OFFER_PLACEMENT_DOC), {
        auctionId,
        bidId: winner.id,
        uid: winner.uid,
        imageUrl: winner.imageUrl,
        link: winner.link,
        settledAtMs: now,
      });
    }
    if (!nextSnapshot.exists) {
      transaction.create(nextRef, {
        auctionId: next.auctionId,
        status: OFFER_AUCTION_OPEN,
        opensAtMs: next.opensAtMs,
        endsAtMs: next.endsAtMs,
        createdAtMs: now,
        updatedAtMs: now,
      });
    }
    transaction.set(auctionRef, {
      status: OFFER_AUCTION_SETTLING,
      winnerBidId: winner ? winner.id : null,
      settledAtMs: now,
      updatedAtMs: now,
    }, {merge: true});
    return "closed";
  });
  return outcome;
}

/**
 * Возвраты проигравшим одного аукциона: порциями, каждая — своя транзакция.
 *
 * Порция читает только ставки в статусе BID (индекс auctionId + status), читает кошельки до
 * первой записи, и одной транзакцией флипает статусы, начисляет золото и пишет строки книги —
 * источник строки, идентификатор ставки, уникален: возврат случается ровно один раз. Пустой
 * запрос — аукцион закрывается в SETTLED (если его уже не закрыл параллельный прогон).
 *
 * @returns {number} сколько ставок вернул этот прогон.
 */
async function refundOfferBatches({db, auctionId, now, batchSize = OFFER_SETTLE_BATCH}) {
  exactNonNegative(now, "server time");
  if (!Number.isSafeInteger(batchSize) || batchSize <= 0) fail("INVALID_ARGUMENT", "batch size must be a positive integer");
  const bidsCollection = db.collection(OFFER_BIDS_COLLECTION);
  let refunded = 0;
  for (;;) {
    const batch = await db.runTransaction(async (transaction) => {
      const query = bidsCollection
        .where("auctionId", "==", auctionId)
        .where("status", "==", OFFER_BID_STATUS.BID)
        .limit(batchSize);
      const snapshot = await transaction.get(query);
      if (snapshot.docs.length === 0) return 0;
      const users = {};
      const userRefs = [...new Set(snapshot.docs.map((doc) => `${(doc.data() || {}).uid}`))]
        .map((uid) => db.collection("users").doc(uid));
      const userSnapshots = await transaction.getAll(...userRefs);
      for (const [index, ref] of userRefs.entries()) {
        const user = userSnapshots[index].exists ? userSnapshots[index].data() || {} : {};
        users[ref.path] = storedGold(user);
      }
      for (const doc of snapshot.docs) {
        const bid = doc.data() || {};
        const uid = typeof bid.uid === "string" && bid.uid !== "" ? bid.uid : fail("INVALID_ARGUMENT", "stored bid uid is corrupted");
        const amount = exactNonNegative(bid.amount, "stored bid amount");
        transaction.set(bidsCollection.doc(doc.id),
          {status: OFFER_BID_STATUS.REFUNDED, refundedAtMs: now, updatedAtMs: now}, {merge: true});
        transaction.set(db.collection("users").doc(uid),
          {gold: users[`users/${uid}`] + amount, updatedAtMs: now}, {merge: true});
        const row = ledgerEntry({
          uid,
          currency: CURRENCY.GOLD,
          delta: amount,
          reason: REASON.OFFER_BID_REFUND,
          sourceId: doc.id,
          atMs: now,
          details: {auctionId},
        });
        if (row) transaction.set(db.doc(`${LEDGER_PATH}/${row.id}`), row, {merge: true});
      }
      return snapshot.docs.length;
    });
    if (batch === 0) break;
    refunded += batch;
  }
  await db.runTransaction(async (transaction) => {
    const auctionRef = db.collection(OFFER_AUCTIONS_COLLECTION).doc(auctionId);
    const auctionSnapshot = await transaction.get(auctionRef);
    if (!auctionSnapshot.exists) return;
    const auction = auctionSnapshot.data() || {};
    if (auction.status !== OFFER_AUCTION_SETTLING) return;
    const query = bidsCollection
      .where("auctionId", "==", auctionId)
      .where("status", "==", OFFER_BID_STATUS.BID)
      .limit(1);
    const remaining = await transaction.get(query);
    if (remaining.docs.length > 0) return;
    transaction.set(auctionRef, {status: OFFER_AUCTION_SETTLED, updatedAtMs: now}, {merge: true});
  });
  return refunded;
}

/**
 * Весь прогон: закрыть созревшие OPEN, довозвратить SETTLING, ничего не делать лишний раз.
 *
 * @param {object} deps
 * @param {object} deps.db Firestore.
 * @param {number} deps.now серверное время.
 * @returns {Promise<{closed: number, refunded: number, skipped: number}>}
 */
async function settleDueOfferAuctions({db, now, batchSize}) {
  exactNonNegative(now, "server time");
  const auctions = db.collection(OFFER_AUCTIONS_COLLECTION);
  let closed = 0;
  let refunded = 0;
  let skipped = 0;
  const openSnapshot = await auctions.where("status", "==", OFFER_AUCTION_OPEN).limit(OFFER_SETTLE_SWEEP_LIMIT).get();
  for (const doc of openSnapshot.docs) {
    const outcome = await closeOfferAuction({db, auctionId: doc.id, now});
    if (outcome === "closed") closed += 1;
    else skipped += 1;
  }
  const settlingSnapshot = await auctions.where("status", "==", OFFER_AUCTION_SETTLING).limit(OFFER_SETTLE_SWEEP_LIMIT).get();
  for (const doc of settlingSnapshot.docs) {
    refunded += await refundOfferBatches({db, auctionId: doc.id, now, batchSize});
  }
  return {closed, refunded, skipped};
}

module.exports = {
  OFFER_PLACEMENTS_COLLECTION,
  OFFER_PLACEMENT_DOC,
  OFFER_SETTLE_BATCH,
  closeOfferAuction,
  followingMonthSlot,
  pickWinner,
  refundOfferBatches,
  settleDueOfferAuctions,
};
