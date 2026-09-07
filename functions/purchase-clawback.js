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
 * Before any Play call, a private token fence serializes this handler with purchase grant.
 * PENDING_VERIFICATION blocks credit with a retryable response, not a permanent forfeiture.
 * API cancellation/not-found confirms the fence; unknown SKU-less tokens use token-only V2.
 * Unavailable/disagreeing Play and failed economic transactions throw for redelivery: a pending
 * fence may already exist, but no refund acknowledgement or money effect is fabricated.
 */

const {ECONOMY_CONSTANTS_DOC, readEconomyConstants} = require("./economy-constants");
const {CURRENCY, LEDGER_PATH, REASON, ledgerEntry} = require("./currency-ledger");
const {ACCESS_REFUSED_HINT} = require("./purchase-settlement");
const {PREMIUM_CATCHUP, PREMIUM_CATCHUP_PASSES, premiumGrantId, readPremiumEconomy, planPremiumEconomy, writePremiumEconomy} = require("./premium-economy");
const {SOURCE, durationMsForDays} = require("./premium-grants");
const {PURCHASE_REVOCATIONS_COLLECTION, REVOCATION_CONFIRMED, revocationSignal,
  readRevocationFence, pendingRevocation, confirmedRevocation,
  cancellationProofFromV1, cancellationProofFromV2} = require("./purchase-revocations");
const {
  PURCHASE_AUDIT_COLLECTION,
  PURCHASE_SETTLEMENTS_COLLECTION,
  PRODUCT_PREMIUM,
  STATUS_REFUNDED,
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
  CLAWBACK_NOTHING_TO_TAKE,
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
  if (seen.exists && ![CLAWBACK_NOTHING_TO_TAKE, CLAWBACK_PLAY_DISAGREES].includes(seen.data()?.result)) {
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

  const signal = revocationSignal(notification, playApi.packageName);
  if (!signal) {
    log.warn("playPurchaseNotifications: unsupported full-revocation signal; no token fence", context);
    return record(notificationRef, {messageId, notification, kind, result: CLAWBACK_OUTCOME_RECORDED,
      publishTimeMs, now, detail: "not a valid supported one-time full cancellation"});
  }

  const id = settlementId(token);
  const settlementRef = db.collection(PURCHASE_SETTLEMENTS_COLLECTION).doc(id);
  const revocationRef = db.collection(PURCHASE_REVOCATIONS_COLLECTION).doc(id);
  // The first transaction is the serialization point shared with grant. Reading ABSENT outside
  // this transaction can never justify acknowledging a refund. Pending fence is not a forfeiture.
  let ingress;
  try {
    ingress = await db.runTransaction(async (transaction) => {
      const [known, revocation, priorNotice] = await transaction.getAll(settlementRef, revocationRef, notificationRef);
      if (priorNotice.exists && priorNotice.data()?.settlementId && priorNotice.data().settlementId !== id) {
        throw new Error("Notification replay token binding disagrees; repair required");
      }
      const settlement = known.exists ? known.data() : null;
      let fence = readRevocationFence(revocation.exists ? revocation.data() : null, {id, packageName});
      if (fence && ((fence.signal.orderId && signal.orderId && fence.signal.orderId !== signal.orderId) ||
          (fence.signal.sku && signal.sku && fence.signal.sku !== signal.sku))) {
        throw new Error("Revocation signal source binding disagrees; repair required");
      }
      if (!fence) fence = pendingRevocation({id, packageName, messageId, signal, now});
      if (settlement?.status === STATUS_REFUNDED) fence = confirmedRevocation(fence,
        {now, messageId, proof: "EXISTING_REFUNDED_SETTLEMENT"});
      if (!revocation.exists || fence.state !== revocation.data().state) transaction.set(revocationRef, fence);
      return {settlement, fence};
    });
  } catch (error) {
    log.error("playPurchaseNotifications: fence transaction failed; nothing was written", {...context, settlementId: id});
    throw error;
  }
  const {settlement} = ingress;
  const gate = settlementClawbackGate(settlement, null);
  if (gate && settlement?.status === STATUS_REFUNDED) {
    return record(notificationRef, {
      messageId, notification, kind, result: gate.decision, settlementId: id,
      sku: settlement && settlement.sku, uid: settlement && settlement.uid,
      publishTimeMs, now, detail: gate.reason,
    });
  }

  if (!settlement) {
    // The topic can arrive before the device, and voidedPurchaseNotification has no SKU. Ask
    // token-only V2. Missing dependency/outage/disagreement keeps a retryable, nonterminal fence.
    let proof = {confirmed: ingress.fence.state === REVOCATION_CONFIRMED};
    if (!proof.confirmed) {
      if (typeof playApi.getProductPurchaseByToken !== "function") throw new Error("Token-only Play lookup unavailable; redeliver");
      proof = cancellationProofFromV2(await playApi.getProductPurchaseByToken(token), signal);
    }
    if (!proof.confirmed) {
      log.warn("playPurchaseNotifications: ownerless refund verification pending; redeliver", {...context, settlementId: id});
      throw new Error("Play refund verification pending; redeliver this notification");
    }
    await db.runTransaction(async (transaction) => {
      const [fresh, revocation] = await transaction.getAll(settlementRef, revocationRef);
      if (fresh.exists) throw new Error("Settlement appeared during refund verification; redeliver");
      const fence = readRevocationFence(revocation.exists ? revocation.data() : null, {id, packageName});
      if (!fence) throw new Error("Purchase revocation fence missing; repair required");
      transaction.set(revocationRef, confirmedRevocation(fence, {now, messageId, proof: proof.proof}));
      transaction.set(notificationRef, notificationRecord({messageId, notification, kind,
        result: CLAWBACK_NOTHING_TO_TAKE, settlementId: id, publishTimeMs, now,
        detail: "Play confirmed cancellation before any grant; permanent token fence retained"}));
    });
    return {kind: CLAWBACK_NOTHING_TO_TAKE};
  }
  if (gate) throw new Error("Settlement provenance is not refundable; redeliver for repair");

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

  const proof = cancellationProofFromV1(purchase, settlement, signal);
  if (!proof.confirmed) {
    log.error(`playPurchaseNotifications: ${PLAY_DISAGREES_HINT}`, {...context, settlementId: id, sku, uid});
    throw new Error("Play refund verification pending; redeliver this notification");
  }

  const userRef = db.collection("users").doc(uid);
  const constantsRef = db.doc(ECONOMY_CONSTANTS_DOC);
  let verdict;
  let catchupCheckpoints = 0;
  try {
    for (let pass = 0; pass < PREMIUM_CATCHUP_PASSES; pass++) {
    verdict = await db.runTransaction(async (transaction) => {
      const [settlementSnapshot, userSnapshot, constantsSnapshot, revocationSnapshot] =
        await transaction.getAll(settlementRef, userRef, constantsRef, revocationRef);
      const fresh = settlementSnapshot.exists ? settlementSnapshot.data() : null;
      const fence = readRevocationFence(revocationSnapshot.exists ? revocationSnapshot.data() : null, {id, packageName});
      if (!fence) throw new Error("Purchase revocation fence missing; repair required");
      if (!fresh || fresh.uid !== uid || fresh.sku !== sku) {
        throw new Error("Purchase owner/product changed during refund verification; repair required");
      }
      if (fresh && fresh.status !== STATUS_REFUNDED && !cancellationProofFromV1(purchase, fresh, signal).confirmed) {
        throw new Error("Purchase provenance changed during refund verification; redeliver");
      }
      const constants = readEconomyConstants(constantsSnapshot.exists ? constantsSnapshot.data() : null);
      const decision = decideClawback({settlement: fresh, purchase, uid, now});
      if (decision.decision !== CLAWBACK_CLAW_BACK) return decision;

      const userData = userSnapshot.exists ? userSnapshot.data() || {} : {};
      if (decision.clawback.productKind === PRODUCT_PREMIUM) {
        const operation = {kind: "REFUND", grantId: decision.clawback.premiumGrantId};
        const snapshot = await readPremiumEconomy({db, transaction, uid, operation});
        if (operation.grantId !== premiumGrantId(SOURCE.PLAY, id) || !snapshot.target ||
            snapshot.target.source !== SOURCE.PLAY || snapshot.target.sourceId !== id ||
            snapshot.target.durationMs !== decision.clawback.premiumGrantedMs ||
            snapshot.target.durationMs !== durationMsForDays(decision.clawback.premiumDays)) {
          throw new Error("Premium purchase settlement and grant provenance disagree; repair required");
        }
        const plan = planPremiumEconomy({snapshot, userData, constants, now, operation});
        if (plan.caughtUp && plan.outcome !== "REFUNDED") {
          throw new Error("Premium refund tombstone disagrees with purchase settlement; provenance repair required");
        }
        writePremiumEconomy({db, transaction, uid, plan});
        transaction.set(userRef, {uid, ...plan.userPatch}, {merge: true});
        if (!plan.caughtUp) return {decision: PREMIUM_CATCHUP};
        transaction.set(revocationRef, confirmedRevocation(fence, {now, messageId, proof: proof.proof}));
        const gold = readGold(userData);
        const taken = {
          clawback: decision.clawback, notificationId: messageId,
          goldBefore: gold, goldAfter: gold, shortfallAdded: 0, shortfallAfter: readShortfall(userData),
          premiumRevokedMs: plan.revokedNowMs, premiumConsumedMs: plan.grant.consumedMs,
          premiumUntilMs: plan.premiumUntilMs,
          constantsDoc: ECONOMY_CONSTANTS_DOC, constantsVersion: constants.version, now: plan.header.asOfMs,
        };
        transaction.set(settlementRef, refundSettlementUpdate(taken), {merge: true});
        const refundId = refundDocId(id);
        transaction.set(userRef.collection(RECEIPTS_SUBCOLLECTION).doc(refundId), refundReceiptRecord(taken));
        transaction.set(db.collection(PURCHASE_AUDIT_COLLECTION).doc(refundId), refundAuditRecord(taken));
        transaction.set(notificationRef, notificationRecord({
          messageId, notification, kind, result: CLAWBACK_OUTCOME_CLAWED_BACK, settlementId: id, sku, uid,
          publishTimeMs, now, detail: decision.reason,
        }));
        return {decision: CLAWBACK_CLAW_BACK, taken};
      }
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
      transaction.set(revocationRef, confirmedRevocation(fence, {now, messageId, proof: proof.proof}));

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
    if (verdict.decision !== PREMIUM_CATCHUP) break;
    catchupCheckpoints++;
    }
  } catch (error) {
    // This transaction rolled back. Earlier time-only catch-up checkpoints may have committed;
    // no refund notification is acknowledged until the actual refund commits atomically.
    log.error(catchupCheckpoints ? "playPurchaseNotifications: clawback transaction failed; refund was not committed" :
      "playPurchaseNotifications: clawback transaction failed; nothing was written", {
      ...context, settlementId: id, sku, uid, message: errorMessage(error),
    });
    throw error;
  }

  if (verdict.decision === PREMIUM_CATCHUP) {
    throw new Error("Premium time catch-up made durable progress; redeliver this refund notification");
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
    ...(verdict.taken.clawback.productKind === PRODUCT_PREMIUM ? {
      premiumRevokedMs: verdict.taken.premiumRevokedMs,
      premiumConsumedMs: verdict.taken.premiumConsumedMs,
      premiumUntilMs: verdict.taken.premiumUntilMs,
    } : {}),
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
