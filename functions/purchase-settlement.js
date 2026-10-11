"use strict";

/**
 * Расчёт по чеку: путь денег от запроса до записей, исполняемый без firebase-admin.
 *
 * index.js остаётся обвязкой: аутентификация, сборка зависимостей, перевод исхода в HttpsError.
 * Всё между — здесь, и Firestore приходит параметром. В тестах это подделка, которая запоминает,
 * что и куда было бы написано, — и проверяется само тело транзакции, а не его пересказ.
 *
 * Порядок: форма запроса → запись расчёта (повтор отвечается до Play) → Play → одна транзакция:
 * запись расчёта ещё раз (гонка), пользователь, таблица констант — решение — баланс, расчёт,
 * квитанция, аудит и строка книги движений. Золото пишется абсолютом: `increment` не отличает
 * повтор от первой доставки. Строки книги нет только тогда, когда баланс не двинулся — всё
 * начисленное ушло на недостачу, оставленную возвратом (purchase-clawback.js).
 */

const {ECONOMY_CONSTANTS_DOC, readEconomyConstants} = require("./economy-constants");
const {CURRENCY, LEDGER_PATH, REASON, ledgerEntry} = require("./currency-ledger");
const {SOURCE} = require("./premium-grants");
const {PURCHASE_REVOCATIONS_COLLECTION, SETTLEMENT_REVOCATION_PENDING,
  readRevocationFence, revocationSettlementGate, partialRefundGrantAdjustment,
  multiQuantityGrantProofFromV2} = require("./purchase-revocations");
const {PREMIUM_CATCHUP, PREMIUM_CATCHUP_PASSES, readPremiumEconomy, planPremiumEconomy, writePremiumEconomy} = require("./premium-economy");
const {
  PURCHASE_AUDIT_COLLECTION,
  PURCHASE_SETTLEMENTS_COLLECTION,
  PRODUCT_PREMIUM,
  PURCHASE_STATE_PURCHASED,
  REASON_PURCHASE_CANCELED,
  REASON_REQUEST_INVALID,
  REASON_SKU_NOT_SOLD,
  RECEIPTS_SUBCOLLECTION,
  SETTLEMENT_CREDIT,
  SETTLEMENT_REFUSE,
  SHORTFALL_FIELD,
  auditRecord,
  decideSettlement,
  goldPackAmount,
  premiumProduct,
  purchaseFromLookup,
  readShortfall,
  subscriptionFromLookup,
  receiptRecord,
  settlementId,
  settlementRecord,
  successResponse,
  validatePurchaseRequest,
} = require("./purchase-verification");

/**
 * Исходы расчёта — то, что обвязка переводит в ответ или HttpsError.
 *
 * `response` — ответ клиенту как есть (начислено, повтор, ожидание). `refuse` — окончательный
 * отказ с кодом HTTPS и машинной причиной. `unavailable` — вердикта нет, ничего не записано,
 * клиент повторит позже тем же путём.
 */
const SETTLEMENT_OUTCOME_RESPONSE = "response";
const SETTLEMENT_OUTCOME_REFUSE = "refuse";
const SETTLEMENT_OUTCOME_UNAVAILABLE = "unavailable";
/** Продление, которому этот сервер нечего рассчитывать: признано, ничего не записано. */
const SETTLEMENT_OUTCOME_IGNORED = "ignored";

/** Пишется ошибкой: чинится не кодом, а владельцем в Play Console. */
const ACCESS_REFUSED_HINT =
  "Play Developer API refused access — link the function's service account in Play Console " +
  "(Users and permissions) with View financial data + Manage orders";
/** Пишется ошибкой: игрок заплатил, а таблица SKU не знает — чинится записью в таблицу. */
const SKU_NOT_SOLD_HINT =
  "SKU paid for but absent from configs/economy product descriptors — configure it; the purchase stays unconsumed " +
  "and re-presents on next launch";

/**
 * @param {object} deps
 * @param {object} deps.db Firestore: `collection().doc()`, `doc()`, `runTransaction()`; у ссылки
 *   на документ — `get()`; у транзакции — `getAll()`, `set()`, `create()`.
 * @param {object} deps.playApi клиент play-developer-api.js.
 * @param {number} deps.now серверное время.
 * @param {string} deps.uid кому начислять.
 * @param {object} deps.payload тело запроса.
 * @param {function(object): number} deps.readGold золото из документа пользователя, как его читает
 *   остальной сервер (`readEconomyBalance(data).gold`).
 * @param {object} deps.log логгер с `warn` и `error`.
 * @returns {Promise<{kind: string, response?: object, code?: string, reason?: string, reasonCode?: string}>}
 */
async function settlePurchase({db, playApi, now, uid, payload, readGold, log}) {
  const request = validatePurchaseRequest(payload);
  if (!request.valid) {
    // Тем же путём, что и всякий отказ: одна запись в лог на каждый, и ни одного без неё.
    return outcomeOf(
      {decision: SETTLEMENT_REFUSE, code: "invalid-argument", reason: request.reason, reasonCode: REASON_REQUEST_INVALID},
      {id: null, sku: null, log},
    );
  }
  const {purchaseToken, sku} = request;
  const id = settlementId(purchaseToken);
  const settlementRef = db.collection(PURCHASE_SETTLEMENTS_COLLECTION).doc(id);
  const revocationRef = db.collection(PURCHASE_REVOCATIONS_COLLECTION).doc(id);

  // Известный токен отвечается до того, как спрошен Play. Повтору проверка не нужна — сохранённый
  // ответ и есть проверка, — а после погашения Play ответил бы «погашена», что для токена без
  // записи расчёта было бы отказом. Чужой токен отклоняется здесь же, не выдавая, что он есть.
  // Подписка — исключение по состоянию, не по владельцу: у премиум-записи с записанным сроком
  // вердикта здесь нет (гейт молчит), потому что тот же токен может приехать продлением, и узнает
  // об этом только свежий ответ Play.
  const known = await settlementRef.get();
  const revocation = await revocationRef.get();
  let fence;
  try { fence = readRevocationFence(revocation.exists ? revocation.data() : null, {id, packageName: playApi.packageName}); }
  catch (_) { return {kind: SETTLEMENT_OUTCOME_UNAVAILABLE, reason: "Purchase verification requires repair; retry later"}; }
  const gate = revocationSettlementGate({fence, existing: known.exists ? known.data() : null, uid});
  if (gate) return outcomeOf(gate, {id, sku, log});

  // Класс товара решает, у какого endpoint'а Play спрашивать факт (история 2.1): подписки живут в
  // subscriptionsv2 и отвечают другой формой, одноразовые товары — в purchases.products. Таблица
  // читается дважды — здесь для выбора endpoint'а и в транзакции для решения, — но решение всегда
  // принимает та версия, что внутри транзакции. SKU в обеих таблицах сразу — испорченный конфиг, и
  // спрашивается старый товарный endpoint: у золотого токена он и есть дом правды, а решение всё
  // равно откажет или потребует повтора — испорченная таблица не меняет этого.
  const constantsRef = db.doc(ECONOMY_CONSTANTS_DOC);
  let routingConstants;
  try {
    routingConstants = readEconomyConstants((await constantsRef.get()).data() || null);
  } catch (_) {
    // Таблица не читается — выбрать endpoint нельзя, а значит вердикта о токене нет. Как и
    // недоступный Play: клиент повторит позже тем же путём, покупка до тех пор непогашена.
    return {kind: SETTLEMENT_OUTCOME_UNAVAILABLE, reason: "Purchase verification requires repair; retry later"};
  }
  const isSubscriptionSku =
    premiumProduct(routingConstants, sku) !== null && goldPackAmount(routingConstants, sku) === null;
  const lookup = isSubscriptionSku ?
    await playApi.getSubscriptionPurchaseV2(purchaseToken) :
    await playApi.getProductPurchase(sku, purchaseToken);
  const {unavailable, purchase} = isSubscriptionSku ?
    subscriptionFromLookup(lookup) : purchaseFromLookup(lookup);
  if (unavailable) {
    // Не вердикт о токене. Клиент повторит позже тем же путём, покупка до тех пор остаётся
    // непогашенной — ожиданием ничего не теряется.
    const context = {
      settlementId: id,
      sku,
      outcome: field(lookup, "outcome"),
      status: field(lookup, "status"),
      code: field(lookup, "code"),
      reason: field(lookup, "reason"),
    };
    if (context.status === 401 || context.status === 403) {
      log.error(`verifyPurchase: ${ACCESS_REFUSED_HINT}`, context);
    } else {
      log.warn("verifyPurchase: Play Developer API unavailable", context);
    }
    return {kind: SETTLEMENT_OUTCOME_UNAVAILABLE, reason: "Play could not be reached; retry later"};
  }

  // A new multi-quantity gold grant must additionally agree with ProductPurchaseV2.  We first
  // exclude pending/cancelled/unsold/premium paths with the same pure decision used in the
  // transaction, so Premium q>1 remains an explicit unsupported refusal rather than a V2 outage.
  let multiQuantityProof = null;
  if (purchase && purchase.purchaseState === PURCHASE_STATE_PURCHASED && purchase.quantity > 1) {
    let preflight;
    try {
      const constantsSnapshot = await constantsRef.get();
      preflight = decideSettlement({
        existing: null, purchase, claimedSku: sku, uid, now,
        constants: readEconomyConstants(constantsSnapshot.exists ? constantsSnapshot.data() : null),
      });
    } catch (error) {
      log.warn("verifyPurchase: quantity preflight could not read server constants", {
        settlementId: id, sku, uid, message: errorMessage(error),
      });
      return {kind: SETTLEMENT_OUTCOME_UNAVAILABLE, reason: "Purchase quantity verification unavailable; retry later"};
    }
    if (preflight.decision === SETTLEMENT_CREDIT && preflight.credit.productKind !== PRODUCT_PREMIUM) {
      let v2Lookup;
      try {
        if (typeof playApi.getProductPurchaseByToken !== "function") {
          return {kind: SETTLEMENT_OUTCOME_UNAVAILABLE, reason: "Purchase quantity verification unavailable; retry later"};
        }
        v2Lookup = await playApi.getProductPurchaseByToken(purchaseToken);
      } catch (error) {
        log.warn("verifyPurchase: ProductPurchaseV2 quantity lookup failed", {
          settlementId: id, sku, uid, message: errorMessage(error),
        });
        return {kind: SETTLEMENT_OUTCOME_UNAVAILABLE, reason: "Purchase quantity verification unavailable; retry later"};
      }
      multiQuantityProof = multiQuantityGrantProofFromV2(v2Lookup, purchase);
      if (!multiQuantityProof.confirmed) {
        log.warn("verifyPurchase: ProductPurchaseV2 quantity proof is unavailable or disagrees", {
          settlementId: id, sku, uid, outcome: field(v2Lookup, "outcome"), status: field(v2Lookup, "status"),
        });
        return {kind: SETTLEMENT_OUTCOME_UNAVAILABLE, reason: "Purchase quantity verification unavailable; retry later"};
      }
      if (multiQuantityProof.refundableQuantity === 0) {
        return outcomeOf({
          decision: SETTLEMENT_REFUSE,
          code: "failed-precondition",
          reason: "Play reports no remaining refundable quantity for this purchase",
          reasonCode: REASON_PURCHASE_CANCELED,
        }, {id, sku, log});
      }
    }
  }

  const userRef = db.collection("users").doc(uid);
  let verdict;
  let catchupCheckpoints = 0;
  try {
    for (let pass = 0; pass < PREMIUM_CATCHUP_PASSES; pass++) {
    verdict = await db.runTransaction(async (transaction) => {
      const [settlementSnapshot, userSnapshot, constantsSnapshot, revocationSnapshot] =
        await transaction.getAll(settlementRef, userRef, constantsRef, revocationRef);
      const existing = settlementSnapshot.exists ? settlementSnapshot.data() : null;
      const freshFence = readRevocationFence(revocationSnapshot.exists ? revocationSnapshot.data() : null,
        {id, packageName: playApi.packageName});
      const fenced = revocationSettlementGate({fence: freshFence, existing, uid});
      if (fenced) return fenced;
      const constants = readEconomyConstants(constantsSnapshot.exists ? constantsSnapshot.data() : null);
      // Pure V1 rules (state, readable positive quantity, SKU and Premium q=1) must decide
      // before a grant-only adjustment.  Otherwise a cancelled/zero-quantity purchase would
      // become a retryable provenance error instead of its established terminal refusal.
      let decision = decideSettlement({existing, purchase, claimedSku: sku, uid, constants, now});
      if (decision.decision !== SETTLEMENT_CREDIT) return decision;
      const multiGold = decision.credit.productKind !== PRODUCT_PREMIUM && purchase && purchase.quantity > 1;
      if (multiGold && (!multiQuantityProof || multiQuantityProof.confirmed !== true)) {
        // Config can change between the non-transactional V2 preflight and this authoritative
        // transaction.  A newly enabled multi-quantity SKU never receives the V1 amount without
        // its matching V2 proof; retry performs the required lookup under the new configuration.
        throw new Error("Multi-quantity grant requires matching ProductPurchaseV2 proof; retry later");
      }
      const adjustment = partialRefundGrantAdjustment({
        fence: freshFence, purchase, uid, grantProof: multiGold ? multiQuantityProof : null,
      });
      if (!adjustment.valid) {
        throw new Error("Partial refund provenance disagrees with purchase; repair required");
      }
      decision = decideSettlement({
        existing, purchase, claimedSku: sku, uid, constants, creditedQuantity: adjustment.quantity, now,
      });
      if (decision.decision !== SETTLEMENT_CREDIT) return decision;

      const userData = userSnapshot.exists ? userSnapshot.data() || {} : {};
      if (decision.credit.productKind === PRODUCT_PREMIUM) {
        // Подписка живёт через продления: грант каждого оплаченного срока называется токеном и
        // этим сроком (история 2.3). Тот же срок — тот же грант: переигранная транзакция узнаёт
        // свой лот и не заводит второй; сдвинувшийся вперёд срок Play — новый лот поверх старых,
        // и дата окончания становится max(сейчас, окончание) + срок.
        const termId = `${id}_${decision.credit.subscriptionExpiryMs}`;
        const renewals = existing ? settledRenewals(existing) + 1 : 0;
        const operation = {kind: "GRANT", source: SOURCE.PLAY, sourceId: termId, days: decision.credit.premiumDays};
        const snapshot = await readPremiumEconomy({db, transaction, uid, operation});
        const plan = planPremiumEconomy({snapshot, userData, constants, now, operation});
        if (plan.caughtUp && plan.outcome !== "GRANTED") {
          throw new Error("Premium lot exists without its atomic purchase settlement; provenance repair required");
        }
        writePremiumEconomy({db, transaction, uid, plan});
        transaction.set(userRef, {uid, ...plan.userPatch}, {merge: true});
        if (!plan.caughtUp) return {decision: PREMIUM_CATCHUP};
        const gold = readGold(userData);
        const premium = {premiumGrantId: plan.grant.grantId, premiumUntilMs: plan.premiumUntilMs};
        const response = successResponse({
          sku, goldGranted: 0, gold, settlementId: id, productKind: PRODUCT_PREMIUM,
          premiumGrantedMs: decision.credit.premiumGrantedMs, ...premium,
        });
        const settled = {
          id, docId: termId, uid, purchase, credit: decision.credit, goldBefore: gold, goldAfter: gold,
          constantsDoc: ECONOMY_CONSTANTS_DOC, now: plan.header.asOfMs, partialRefund: adjustment.partial, ...premium,
        };
        const record = settlementRecord({...settled, response});
        // Первый срок заводит запись `create`-ом: если параллельный вызов рассчитал токен первым,
        // транзакция падает и на повторе отвечает повтором. Продление запись обновляет — она о
        // новом сроке того же токена, а повторную выдачу того же срока отсёк лот.
        if (existing) transaction.set(settlementRef, {...record, subscriptionRenewals: renewals});
        else transaction.create(settlementRef, record);
        transaction.set(userRef.collection(RECEIPTS_SUBCOLLECTION).doc(termId), receiptRecord(settled));
        transaction.set(db.collection(PURCHASE_AUDIT_COLLECTION).doc(termId), auditRecord(settled));
        return {decision: SETTLEMENT_CREDIT, response};
      }
      const goldBefore = readGold(userData);
      // Долг гасится до баланса. Возврат, которому нечего было снять, оставил недостачу (ADM-19), и
      // следующее начисление сначала закрывает её, а до баланса доходит остаток. Иначе игрок,
      // потративший возвращённое, покупал бы себе прощение долга вместе с новым паком.
      const shortfallBefore = readShortfall(userData);
      const shortfallCleared = Math.min(shortfallBefore, decision.credit.goldGranted);
      const shortfallAfter = shortfallBefore - shortfallCleared;
      const goldAfter = goldBefore + decision.credit.goldGranted - shortfallCleared;
      const response = successResponse({
        sku, goldGranted: decision.credit.goldGranted, gold: goldAfter, settlementId: id,
      });
      const settled = {
        id, uid, purchase, credit: decision.credit, goldBefore, goldAfter,
        constantsDoc: ECONOMY_CONSTANTS_DOC, now, partialRefund: adjustment.partial,
      };
      transaction.set(
        userRef,
        {uid, gold: goldAfter, [SHORTFALL_FIELD]: shortfallAfter, updatedAtMs: now},
        {merge: true},
      );
      // `create`, а не `set`: если между чтением и фиксацией этот токен рассчитал параллельный
      // вызов, транзакция падает — и на повторе читает запись и отвечает повтором — вместо того,
      // чтобы начислить второй раз.
      transaction.create(settlementRef, settlementRecord({...settled, response}));
      transaction.set(userRef.collection(RECEIPTS_SUBCOLLECTION).doc(id), receiptRecord(settled));
      transaction.set(db.collection(PURCHASE_AUDIT_COLLECTION).doc(id), auditRecord(settled));
      // Деньги были единственным местом, куда книга движений не заглядывала. Записывается настоящее
      // движение баланса — выданное минус то, что ушло на долг, — а полная сумма и погашенная
      // недостача названы в подробностях: сумма строк обязана сходиться с балансом.
      const entry = ledgerEntry({
        uid,
        currency: CURRENCY.GOLD,
        delta: goldAfter - goldBefore,
        reason: REASON.GOLD_PACK,
        sourceId: id,
        atMs: now,
        details: {
          sku,
          goldGranted: decision.credit.goldGranted,
          quantity: decision.credit.quantity,
          shortfallCleared,
          shortfallAfter,
        },
      });
      if (entry) transaction.set(db.doc(`${LEDGER_PATH}/${entry.id}`), entry, {merge: true});
      return {decision: SETTLEMENT_CREDIT, response};
    });
    if (verdict.decision !== PREMIUM_CATCHUP) break;
    catchupCheckpoints++;
    }
  } catch (error) {
    // This transaction rolled back. Earlier time-only catch-up checkpoints may have committed;
    // no purchase credit/receipt is committed until the final atomic transaction succeeds.
    log.error(catchupCheckpoints ? "verifyPurchase: settlement transaction failed; purchase was not committed" :
      "verifyPurchase: settlement transaction failed; nothing was written", {
      settlementId: id, sku, uid, message: errorMessage(error),
    });
    return {kind: SETTLEMENT_OUTCOME_UNAVAILABLE, reason: "settlement could not be committed; retry later"};
  }
  if (verdict.decision === PREMIUM_CATCHUP) return {
    kind: SETTLEMENT_OUTCOME_UNAVAILABLE,
    reason: "Premium time catch-up made durable progress; retry the unconsumed purchase",
  };
  return outcomeOf(verdict, {id, sku, log});
}

/** Сколько сроков этого токена уже рассчитано: поле появляется с первым продлением. */
function settledRenewals(existing) {
  const parsed = Number(existing && existing.subscriptionRenewals);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : 0;
}

/**
 * Продление по уведомлению Play (история 2.3): тот же расчёт, другой повод.
 *
 * В сообщении нет ни uid, ни SKU — оба живут в записи расчёта, которую токен уже завёл, покупая
 * подписку с устройства. Дальше работает тот же `settlePurchase` без единой уступки: свежий ответ
 * Play, тот же гейт, та же транзакция, тот же грант «токен и срок». Записи нет — токен никогда не
 * начислялся (подписка куплена до этого сервера или чужая): сообщение признаётся и не повторяется,
 * потому что повтор не создаст того, что отсутствовало. Вердикта нет — ошибка: сообщение приедет
 * снова, и тогда, возможно, Play ответит.
 *
 * @returns {Promise<{kind: string, response?: object}>} исход `settlePurchase` или `IGNORED`.
 */
async function settleSubscriptionRenewal({db, playApi, now, purchaseToken, readGold, log}) {
  const id = settlementId(purchaseToken);
  const known = await db.collection(PURCHASE_SETTLEMENTS_COLLECTION).doc(id).get();
  if (!known.exists) {
    log.warn("settleSubscriptionRenewal: renewal for a token this server never settled; acknowledged", {
      settlementId: id,
    });
    return {kind: SETTLEMENT_OUTCOME_IGNORED};
  }
  const record = known.data() || {};
  return settlePurchase({
    db, playApi, now, uid: stringValue(record.uid),
    payload: {purchaseToken, sku: stringValue(record.sku)},
    readGold, log,
  });
}

function stringValue(value) {
  return value === null || value === undefined ? "" : String(value);
}

/** Переводит чистый вердикт в исход: ответ как есть, или отказ с кодом и причиной. */
function outcomeOf(verdict, {id, sku, log}) {
  if (verdict.decision === SETTLEMENT_REVOCATION_PENDING) return {
    kind: SETTLEMENT_OUTCOME_UNAVAILABLE, reason: "Play refund verification is pending; retry later",
  };
  if (verdict.decision !== SETTLEMENT_REFUSE) {
    return {kind: SETTLEMENT_OUTCOME_RESPONSE, response: verdict.response};
  }
  const context = {
    settlementId: id, sku, code: verdict.code, reasonCode: verdict.reasonCode, reason: verdict.reason,
  };
  if (verdict.reasonCode === REASON_SKU_NOT_SOLD) {
    log.error(`verifyPurchase: ${SKU_NOT_SOLD_HINT}`, context);
  } else {
    log.warn("verifyPurchase: refused", context);
  }
  return refusal(verdict);
}

function refusal({code, reason, reasonCode}) {
  return {kind: SETTLEMENT_OUTCOME_REFUSE, code, reason, reasonCode};
}

function field(source, name) {
  return source && source[name] !== undefined ? source[name] : null;
}

function errorMessage(error) {
  return error && error.message ? error.message : String(error);
}

module.exports = {
  ACCESS_REFUSED_HINT,
  SETTLEMENT_OUTCOME_REFUSE,
  SETTLEMENT_OUTCOME_RESPONSE,
  SETTLEMENT_OUTCOME_UNAVAILABLE,
  SETTLEMENT_OUTCOME_IGNORED,
  SKU_NOT_SOLD_HINT,
  settlePurchase,
  settleSubscriptionRenewal,
};
