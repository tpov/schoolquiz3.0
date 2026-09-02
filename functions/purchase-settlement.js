"use strict";

/**
 * Расчёт по чеку: путь денег от запроса до записей, исполняемый без firebase-admin.
 *
 * index.js остаётся обвязкой: аутентификация, сборка зависимостей, перевод исхода в HttpsError.
 * Всё между — здесь, и Firestore приходит параметром. В тестах это подделка, которая запоминает,
 * что и куда было бы написано, — и проверяется само тело транзакции, а не его пересказ.
 *
 * Порядок: форма запроса → запись расчёта (повтор отвечается до Play) → Play → одна транзакция:
 * запись расчёта ещё раз (гонка), пользователь, таблица констант — решение — четыре записи.
 * Золото пишется абсолютом: `increment` не отличает повтор от первой доставки.
 */

const {ECONOMY_CONSTANTS_DOC, readEconomyConstants} = require("./economy-constants");
const {
  PURCHASE_AUDIT_COLLECTION,
  PURCHASE_SETTLEMENTS_COLLECTION,
  REASON_REQUEST_INVALID,
  REASON_SKU_NOT_SOLD,
  RECEIPTS_SUBCOLLECTION,
  SETTLEMENT_CREDIT,
  SETTLEMENT_REFUSE,
  auditRecord,
  decideSettlement,
  purchaseFromLookup,
  receiptRecord,
  settlementGate,
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

/** Пишется ошибкой: чинится не кодом, а владельцем в Play Console. */
const ACCESS_REFUSED_HINT =
  "Play Developer API refused access — link the function's service account in Play Console " +
  "(Users and permissions) with View financial data + Manage orders";
/** Пишется ошибкой: игрок заплатил, а таблица SKU не знает — чинится записью в таблицу. */
const SKU_NOT_SOLD_HINT =
  "SKU paid for but absent from configs/economy goldPacks — add it; the purchase stays unconsumed " +
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

  // Известный токен отвечается до того, как спрошен Play. Повтору проверка не нужна — сохранённый
  // ответ и есть проверка, — а после погашения Play ответил бы «погашена», что для токена без
  // записи расчёта было бы отказом. Чужой токен отклоняется здесь же, не выдавая, что он есть.
  const known = await settlementRef.get();
  const gate = settlementGate(known.exists ? known.data() : null, uid);
  if (gate) return outcomeOf(gate, {id, sku, log});

  const lookup = await playApi.getProductPurchase(sku, purchaseToken);
  const {unavailable, purchase} = purchaseFromLookup(lookup);
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

  const userRef = db.collection("users").doc(uid);
  const constantsRef = db.doc(ECONOMY_CONSTANTS_DOC);
  let verdict;
  try {
    verdict = await db.runTransaction(async (transaction) => {
      const [settlementSnapshot, userSnapshot, constantsSnapshot] =
        await transaction.getAll(settlementRef, userRef, constantsRef);
      const existing = settlementSnapshot.exists ? settlementSnapshot.data() : null;
      const constants = readEconomyConstants(constantsSnapshot.exists ? constantsSnapshot.data() : null);
      const decision = decideSettlement({existing, purchase, claimedSku: sku, uid, constants});
      if (decision.decision !== SETTLEMENT_CREDIT) return decision;

      const goldBefore = readGold(userSnapshot.exists ? userSnapshot.data() || {} : {});
      const goldAfter = goldBefore + decision.credit.goldGranted;
      const response = successResponse({
        sku, goldGranted: decision.credit.goldGranted, gold: goldAfter, settlementId: id,
      });
      const settled = {
        id, uid, purchase, credit: decision.credit, goldBefore, goldAfter,
        constantsDoc: ECONOMY_CONSTANTS_DOC, now,
      };
      transaction.set(userRef, {uid, gold: goldAfter, updatedAtMs: now}, {merge: true});
      // `create`, а не `set`: если между чтением и фиксацией этот токен рассчитал параллельный
      // вызов, транзакция падает — и на повторе читает запись и отвечает повтором — вместо того,
      // чтобы начислить второй раз.
      transaction.create(settlementRef, settlementRecord({...settled, response}));
      transaction.set(userRef.collection(RECEIPTS_SUBCOLLECTION).doc(id), receiptRecord(settled));
      transaction.set(db.collection(PURCHASE_AUDIT_COLLECTION).doc(id), auditRecord(settled));
      return {decision: SETTLEMENT_CREDIT, response};
    });
  } catch (error) {
    // Транзакция не зафиксировалась: исчерпаны попытки на состязании, или `create` наткнулся на
    // расчёт, которого не было при чтении. Ничего не записано, и повтор — правильный ответ.
    log.error("verifyPurchase: settlement transaction failed; nothing was written", {
      settlementId: id, sku, uid, message: errorMessage(error),
    });
    return {kind: SETTLEMENT_OUTCOME_UNAVAILABLE, reason: "settlement could not be committed; retry later"};
  }
  return outcomeOf(verdict, {id, sku, log});
}

/** Переводит чистый вердикт в исход: ответ как есть, или отказ с кодом и причиной. */
function outcomeOf(verdict, {id, sku, log}) {
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
  SKU_NOT_SOLD_HINT,
  settlePurchase,
};
