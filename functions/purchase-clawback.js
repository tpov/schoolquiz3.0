"use strict";

/**
 * Возврат: путь денег от сообщения Play до записей, исполняемый без firebase-admin.
 *
 * Зеркало purchase-settlement.js, и по той же причине: index.js остаётся триггером, а всё между
 * сообщением и записями лежит здесь, где Firestore — параметр. В тестах это подделка, которая
 * запоминает, что и куда было бы написано, — и проверяется само тело транзакции, а не пересказ.
 *
 * Порядок: сообщение уже виденное (повтор отвечается до всего) → чтение → наш ли пакет → о чём
 * уведомление → запись расчёта (повтор снятия отсекается до Play) → Play подтверждает, что покупки
 * больше нет → одна транзакция: запись расчёта ещё раз (гонка), пользователь, таблица констант —
 * решение — баланс, отметка о возврате, квитанция, аудит, строка книги, запись о сообщении.
 *
 * Что подтверждается, а что повторяется. Нечитаемое сообщение, чужой пакет, неизвестный вид, токен
 * без расчёта — подтверждаются: повторять их нечем и незачем, а не подтверждать значит просить
 * Pub/Sub слать одно и то же каждые несколько секунд. Недоступный Play и несостоявшаяся транзакция
 * — бросаются: ничего не записано, и снять всё-таки надо, поэтому пусть привезут снова.
 */

const {ECONOMY_CONSTANTS_DOC, readEconomyConstants} = require("./economy-constants");
const {CURRENCY, LEDGER_PATH, REASON, ledgerEntry} = require("./currency-ledger");
const {ACCESS_REFUSED_HINT} = require("./purchase-settlement");
const {
  PURCHASE_AUDIT_COLLECTION,
  PURCHASE_SETTLEMENTS_COLLECTION,
  RECEIPTS_SUBCOLLECTION,
  SHORTFALL_FIELD,
  goldAfterClawback,
  purchaseFromLookup,
  readShortfall,
  settlementId,
} = require("./purchase-verification");
const {
  CLAWBACK_CLAW_BACK,
  CLAWBACK_PLAY_DISAGREES,
  NOTIFICATION_UNKNOWN,
  NOTIFICATION_VOID,
  PURCHASE_NOTIFICATIONS_COLLECTION,
  decideClawback,
  decodeNotification,
  notificationKind,
  notificationRecord,
  packageOf,
  refundAuditRecord,
  refundDocId,
  refundReceiptRecord,
  refundSettlementUpdate,
  settlementClawbackGate,
  tokenOf,
} = require("./purchase-notifications");

/**
 * Исходы обработки сообщения. Три из них — решения по возврату (`CLAWBACK_*`), остальные — про само
 * сообщение: снято, уже виденное, записано и больше ничего, нечитаемо, не наш пакет.
 */
const CLAWBACK_OUTCOME_CLAWED_BACK = "CLAWED_BACK";
const CLAWBACK_OUTCOME_REPLAYED = "REPLAYED";
const CLAWBACK_OUTCOME_RECORDED = "RECORDED";
const CLAWBACK_OUTCOME_UNREADABLE = "UNREADABLE";
const CLAWBACK_OUTCOME_IGNORED = "IGNORED";

/** Пишется ошибкой: Play подтвердил покупку, а уведомление говорило о возврате. */
const PLAY_DISAGREES_HINT =
  "Play still reports this purchase as bought while a voided-purchase notification arrived — " +
  "nothing was taken back; check the order in Play Console before touching the balance by hand";

/**
 * @param {object} deps
 * @param {object} deps.db Firestore: `collection().doc()`, `doc()`, `runTransaction()`; у ссылки на
 *   документ — `get()` и `set()`; у транзакции — `getAll()` и `set()`.
 * @param {object} deps.playApi клиент play-developer-api.js; его же `packageName` решает, наше ли
 *   это сообщение.
 * @param {number} deps.now серверное время.
 * @param {object} deps.message сообщение топика: `messageId`, `data` (base64), `publishTime`.
 * @param {function(object): number} deps.readGold золото из документа пользователя, как его читает
 *   остальной сервер (`readEconomyBalance(data).gold`).
 * @param {object} deps.log логгер с `warn` и `error`.
 * @returns {Promise<{kind: string}>}
 */
async function clawBackPurchase({db, playApi, now, message, readGold, log}) {
  const messageId = stringValue(message && message.messageId);
  const publishTimeMs = millisOf(message && message.publishTime);
  if (!messageId) {
    // Pub/Sub всегда его ставит. Без него запись некуда положить, а повторять нечего.
    log.error("playPurchaseNotifications: message has no id; nothing to key on", {publishTimeMs});
    return {kind: CLAWBACK_OUTCOME_UNREADABLE};
  }
  const notificationRef = db.collection(PURCHASE_NOTIFICATIONS_COLLECTION).doc(messageId);

  // Уже виденное сообщение отвечается своим же сохранённым результатом и не пишет ничего. Это
  // экономия, а не защита денег: их охраняет состояние записи расчёта ниже.
  const seen = await notificationRef.get();
  if (seen.exists) {
    const stored = seen.data() || {};
    return {kind: stringValue(stored.result) || CLAWBACK_OUTCOME_REPLAYED, replayed: true};
  }

  const context = {messageId, publishTimeMs};
  const notification = decodeNotification(message);
  if (!notification) {
    log.warn("playPurchaseNotifications: message could not be read", context);
    return record(notificationRef, {
      messageId, notification: null, kind: NOTIFICATION_UNKNOWN, result: CLAWBACK_OUTCOME_UNREADABLE,
      publishTimeMs, now, detail: "data is missing, not base64, or not a JSON object",
    });
  }

  const packageName = packageOf(notification);
  if (packageName !== stringValue(playApi.packageName)) {
    // Чужой пакет на нашем топике — не ошибка обработчика и не наши деньги. Логируется и не
    // записывается: заводить документ на каждое чужое сообщение значит платить за чужую настройку.
    log.warn("playPurchaseNotifications: notification for another package", {...context, packageName});
    return {kind: CLAWBACK_OUTCOME_IGNORED, packageName};
  }

  const kind = notificationKind(notification);
  if (kind !== NOTIFICATION_VOID) {
    // Покупка, тест и незнакомый вид: записать и подтвердить. По покупке начисляет предъявленный
    // устройством чек — второй путь к деньгам не заводится.
    return record(notificationRef, {
      messageId, notification, kind, result: CLAWBACK_OUTCOME_RECORDED, publishTimeMs, now,
    });
  }

  const token = tokenOf(notification);
  if (!token) {
    log.warn("playPurchaseNotifications: voided-purchase notification without a token", context);
    return record(notificationRef, {
      messageId, notification, kind, result: CLAWBACK_OUTCOME_UNREADABLE, publishTimeMs, now,
      detail: "voided purchase notification carries no purchaseToken",
    });
  }

  const id = settlementId(token);
  const settlementRef = db.collection(PURCHASE_SETTLEMENTS_COLLECTION).doc(id);
  const known = await settlementRef.get();
  const settlement = known.exists ? known.data() : null;
  // Ни разу не начисленный токен и уже возвращённый отвечаются до Play: спрашивать его незачем,
  // снимать нечего.
  const gate = settlementClawbackGate(settlement, null);
  if (gate) {
    return record(notificationRef, {
      messageId, notification, kind, result: gate.decision, settlementId: id,
      sku: settlement && settlement.sku, uid: settlement && settlement.uid,
      publishTimeMs, now, detail: gate.reason,
    });
  }

  const uid = stringValue(settlement.uid);
  const sku = stringValue(settlement.sku);
  const lookup = await playApi.getProductPurchase(sku, token);
  const {unavailable, purchase} = purchaseFromLookup(lookup);
  if (unavailable) {
    // Вердикта нет — значит, и снятия нет. Ничего не записано, сообщение не подтверждено: Pub/Sub
    // привезёт его снова, и тогда Play, возможно, ответит.
    const outage = {
      ...context, settlementId: id, sku,
      outcome: field(lookup, "outcome"), status: field(lookup, "status"),
      code: field(lookup, "code"), reason: field(lookup, "reason"),
    };
    if (outage.status === 401 || outage.status === 403) {
      log.error(`playPurchaseNotifications: ${ACCESS_REFUSED_HINT}`, outage);
    } else {
      log.warn("playPurchaseNotifications: Play Developer API unavailable; message will be redelivered", outage);
    }
    throw new Error("Play Developer API unavailable; redeliver this notification");
  }

  const userRef = db.collection("users").doc(uid);
  const constantsRef = db.doc(ECONOMY_CONSTANTS_DOC);
  let verdict;
  try {
    verdict = await db.runTransaction(async (transaction) => {
      const [settlementSnapshot, userSnapshot, constantsSnapshot] =
        await transaction.getAll(settlementRef, userRef, constantsRef);
      const fresh = settlementSnapshot.exists ? settlementSnapshot.data() : null;
      const constants = readEconomyConstants(constantsSnapshot.exists ? constantsSnapshot.data() : null);
      const decision = decideClawback({settlement: fresh, purchase, uid, now});
      if (decision.decision !== CLAWBACK_CLAW_BACK) return decision;

      const userData = userSnapshot.exists ? userSnapshot.data() || {} : {};
      const goldBefore = readGold(userData);
      const shortfallBefore = readShortfall(userData);
      const after = goldAfterClawback(goldBefore, decision.clawback.goldGranted);
      const shortfallAfter = shortfallBefore + after.shortfall;
      const taken = {
        clawback: decision.clawback,
        notificationId: messageId,
        goldBefore,
        goldAfter: after.gold,
        shortfallAdded: after.shortfall,
        shortfallAfter,
        constantsDoc: ECONOMY_CONSTANTS_DOC,
        constantsVersion: constants.version,
        now,
      };

      // Абсолютом, а не приращением: повторно исполненная транзакция обязана дать тот же баланс.
      // Ноль — пол: недостача выражается своим полем, а не минусом (ADM-19).
      transaction.set(
        userRef,
        {uid, gold: after.gold, [SHORTFALL_FIELD]: shortfallAfter, updatedAtMs: now},
        {merge: true},
      );
      // Слиянием, а не заменой: запись о начислении — доказательство выданного, и она остаётся.
      transaction.set(settlementRef, refundSettlementUpdate(taken), {merge: true});
      const refundId = refundDocId(id);
      transaction.set(userRef.collection(RECEIPTS_SUBCOLLECTION).doc(refundId), refundReceiptRecord(taken));
      transaction.set(db.collection(PURCHASE_AUDIT_COLLECTION).doc(refundId), refundAuditRecord(taken));
      // Движение записывается настоящее — сколько с баланса действительно ушло, — а выданное и
      // недостача названы в подробностях: сумма строк книги обязана сходиться с балансом, иначе
      // сверять по ней нечего.
      const entry = ledgerEntry({
        uid,
        currency: CURRENCY.GOLD,
        delta: after.gold - goldBefore,
        reason: REASON.PURCHASE_REFUND,
        sourceId: id,
        atMs: now,
        details: {
          sku: decision.clawback.sku,
          goldGranted: decision.clawback.goldGranted,
          shortfallAdded: after.shortfall,
          shortfallAfter,
          notificationId: messageId,
        },
      });
      if (entry) transaction.set(db.doc(`${LEDGER_PATH}/${entry.id}`), entry, {merge: true});
      transaction.set(notificationRef, notificationRecord({
        messageId, notification, kind, result: CLAWBACK_OUTCOME_CLAWED_BACK, settlementId: id, sku, uid,
        publishTimeMs, now, detail: decision.reason,
      }));
      return {decision: CLAWBACK_CLAW_BACK, taken};
    });
  } catch (error) {
    // Транзакция не зафиксировалась — ничего не записано. Снять всё-таки надо, поэтому сообщение не
    // подтверждается: Pub/Sub привезёт его снова.
    log.error("playPurchaseNotifications: clawback transaction failed; nothing was written", {
      ...context, settlementId: id, sku, uid, message: errorMessage(error),
    });
    throw error;
  }

  if (verdict.decision !== CLAWBACK_CLAW_BACK) {
    if (verdict.decision === CLAWBACK_PLAY_DISAGREES) {
      log.error(`playPurchaseNotifications: ${PLAY_DISAGREES_HINT}`, {...context, settlementId: id, sku, uid});
    }
    return record(notificationRef, {
      messageId, notification, kind, result: verdict.decision, settlementId: id, sku, uid,
      publishTimeMs, now, detail: verdict.reason,
    });
  }
  return {
    kind: CLAWBACK_OUTCOME_CLAWED_BACK,
    settlementId: id,
    uid,
    goldTakenBack: verdict.taken.goldBefore - verdict.taken.goldAfter,
    shortfall: verdict.taken.shortfallAfter,
  };
}

/** Записывает сообщение и возвращает его же результат: одна запись, один ответ. */
async function record(notificationRef, fields) {
  await notificationRef.set(notificationRecord(fields));
  return {kind: fields.result};
}

function field(source, name) {
  return source && source[name] !== undefined ? source[name] : null;
}

/** `publishTime` приезжает строкой RFC 3339; всё нечитаемое — ноль, а не догадка. */
function millisOf(value) {
  if (value === null || value === undefined) return 0;
  const parsed = typeof value === "number" ? value : Date.parse(String(value));
  return Number.isFinite(parsed) ? Math.max(0, Math.trunc(parsed)) : 0;
}

function errorMessage(error) {
  return error && error.message ? error.message : String(error);
}

function stringValue(value) {
  if (value === null || value === undefined) return "";
  return String(value);
}

module.exports = {
  CLAWBACK_OUTCOME_CLAWED_BACK,
  CLAWBACK_OUTCOME_IGNORED,
  CLAWBACK_OUTCOME_RECORDED,
  CLAWBACK_OUTCOME_REPLAYED,
  CLAWBACK_OUTCOME_UNREADABLE,
  PLAY_DISAGREES_HINT,
  clawBackPurchase,
};
