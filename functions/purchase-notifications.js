"use strict";

/**
 * Уведомления Play о возвратах: чтение сообщения, решение о снятии и записи, которые оно оставляет.
 *
 * `verifyPurchase` выдаёт золото за проверенный чек, и до этой истории ничто его не отбирало.
 * Google возвращает деньги, не спрашивая приложение, и сам возвращает всё, что игрок не погасил за
 * три дня, — то есть возврат был самым дешёвым способом получить золото бесплатно.
 *
 * Уведомления приезжают в топик Pub/Sub. Само уведомление — не факт, а повод спросить: снимается
 * только то, что Play подтвердил своим ответом о покупке (`purchases.products.get`) — покупка не в
 * состоянии «куплена», либо токен ему вовсе не знаком. Сумма берётся из записи расчёта, а не из
 * уведомления: в уведомлении её нет, а если бы была — верить ей значило бы верить чужому числу о
 * своих деньгах.
 *
 * Ключей два, и оба нужны. Id сообщения отвечает на повторную доставку тем же ответом, ничего не
 * записывая. Состояние самой записи расчёта — сторож на случай, когда об одном токене приехали два
 * разных сообщения: уже возвращённая запись не возвращается второй раз. Первый ключ экономит
 * работу, второй охраняет деньги.
 *
 * Модуль чистый — тестируется без firebase-admin: здесь чтение сообщения, решение и формы записей.
 * Транзакция — purchase-clawback.js, запрос к Play — play-developer-api.js, триггер — index.js.
 */

const {
  PURCHASE_STATE_PURCHASED,
  MAX_TOKEN_LENGTH,
  STATUS_CREDITED,
  STATUS_REFUNDED,
  PRODUCT_PREMIUM,
} = require("./purchase-verification");

/** Сообщения топика: один документ на id сообщения. Закрыто правилами, как и расчёты. */
const PURCHASE_NOTIFICATIONS_COLLECTION = "purchase_notifications";

/**
 * О чём уведомление.
 *
 * VOID — деньги вернули (`voidedPurchaseNotification`, или одноразовый товар с типом «отменён»);
 * только этот вид ведёт к снятию. PURCHASED — покупка состоялась: устройство предъявит токен само,
 * и начислять по уведомлению значило бы завести второй путь к деньгам. TEST — проверка настройки
 * топика владельцем. UNKNOWN — подписки и всё, чего этот обработчик не знает: записывается и
 * подтверждается, потому что неизвестное сообщение не повод копить повторные доставки.
 */
const NOTIFICATION_VOID = "VOID";
const NOTIFICATION_PURCHASED = "PURCHASED";
const NOTIFICATION_TEST = "TEST";
const NOTIFICATION_UNKNOWN = "UNKNOWN";

/** `oneTimeProductNotification.notificationType`: 1 — куплено, 2 — отменено. */
const ONE_TIME_PRODUCT_PURCHASED = 1;
const ONE_TIME_PRODUCT_CANCELED = 2;

/**
 * Решение по возврату.
 *
 * CLAW_BACK — снять выданное. ALREADY_REFUNDED — по этому токену уже сняли (второе сообщение о том
 * же возврате). NOTHING_TO_TAKE — токен никогда не начислялся, значит и отбирать нечего.
 * PLAY_DISAGREES — Play всё ещё считает покупку купленной: уведомление не вердикт, и снимать по
 * нему одному нельзя.
 */
const CLAWBACK_CLAW_BACK = "CLAW_BACK";
const CLAWBACK_ALREADY_REFUNDED = "ALREADY_REFUNDED";
const CLAWBACK_NOTHING_TO_TAKE = "NOTHING_TO_TAKE";
const CLAWBACK_PLAY_DISAGREES = "PLAY_DISAGREES";

/** Вид квитанции о возврате и ключ её текста: текст собирает устройство, как и у начисления. */
const RECEIPT_KIND_PURCHASE_REFUNDED = "PURCHASE_REFUNDED";
const RECEIPT_MESSAGE_GOLD_PACK_REFUNDED = "receipt.gold_pack_refunded";
/** Действие в аудите. Парное к `PURCHASE_CREDIT`, тем же порядком полей. */
const AUDIT_ACTION_PURCHASE_REFUND = "PURCHASE_REFUND";

/**
 * Суффиксы документов возврата.
 *
 * Квитанция и аудит начисления лежат под id расчёта; возврат обязан лечь рядом, а не поверх — иначе
 * запись о том, что игроку выдали, исчезла бы ровно тогда, когда её впервые понадобилось прочесть.
 */
const REFUND_DOC_SUFFIX = "_refund";

/**
 * Читает сообщение топика: base64 в `data` → объект.
 *
 * `null`, если читать нечего: поля нет, это не base64, не JSON, или JSON не объект. Дальше такое
 * сообщение записывается как нечитаемое и подтверждается — повторять его бессмысленно, а не
 * подтверждать значит просить Pub/Sub присылать мусор снова каждые несколько секунд.
 */
function decodeNotification(message) {
  const data = message && message.data;
  if (typeof data !== "string" || !data) return null;
  let text;
  try {
    text = Buffer.from(data, "base64").toString("utf8");
  } catch (error) {
    return null;
  }
  if (!text) return null;
  try {
    const parsed = JSON.parse(text);
    return isObject(parsed) ? parsed : null;
  } catch (error) {
    return null;
  }
}

/** О чём уведомление — одно из [NOTIFICATION_VOID] и соседей. */
function notificationKind(notification) {
  if (!isObject(notification)) return NOTIFICATION_UNKNOWN;
  if (isObject(notification.testNotification)) return NOTIFICATION_TEST;
  if (isObject(notification.voidedPurchaseNotification)) return NOTIFICATION_VOID;
  const oneTime = notification.oneTimeProductNotification;
  if (isObject(oneTime)) {
    const type = intOrNull(oneTime.notificationType);
    if (type === ONE_TIME_PRODUCT_PURCHASED) return NOTIFICATION_PURCHASED;
    if (type === ONE_TIME_PRODUCT_CANCELED) return NOTIFICATION_VOID;
  }
  return NOTIFICATION_UNKNOWN;
}

/**
 * Токен покупки из уведомления. Пустая строка — токена нет.
 *
 * Длина ограничена тем же потолком, что и у чека с устройства: токен идёт в путь запроса к Play и
 * в хеш, и мегабайт под видом токена — не токен.
 */
function tokenOf(notification) {
  if (!isObject(notification)) return "";
  const sources = [notification.voidedPurchaseNotification, notification.oneTimeProductNotification];
  for (const source of sources) {
    if (!isObject(source)) continue;
    const token = source.purchaseToken;
    if (typeof token === "string" && token && token.length <= MAX_TOKEN_LENGTH) return token;
  }
  return "";
}

/** Пакет, о котором уведомление. Чужой пакет — не наше дело. */
function packageOf(notification) {
  return isObject(notification) ? stringValue(notification.packageName) : "";
}

/**
 * Вердикт по одной только записи расчёта — до того, как спрашивать Play, и ещё раз внутри
 * транзакции.
 *
 * Тот же порядок, что и у `settlementGate`: сначала владелец, потом состояние. Записи нет — токен
 * никогда не начислялся: возврат чужой покупки или покупки, за которую мы ничего не давали.
 * Состояние «возвращено» — сняли раньше: второе сообщение о том же возврате не снимает второй раз,
 * и это главный сторож, а не id сообщения.
 *
 * @param uid кому собираются снимать; `null` до того, как это известно.
 * @returns {object|null} null, если снимать можно и решает ответ Play.
 */
function settlementClawbackGate(settlement, uid) {
  if (!settlement) return verdict(CLAWBACK_NOTHING_TO_TAKE, "no settlement for this purchase token");
  if (uid !== null && uid !== undefined && stringValue(settlement.uid) !== stringValue(uid)) {
    // Между предварительным чтением и транзакцией запись сменила владельца. Такого не бывает, но
    // писать баланс не тому, чью запись читали, нельзя ни при каких обстоятельствах.
    return verdict(CLAWBACK_NOTHING_TO_TAKE, "settlement belongs to another account");
  }
  if (settlement.status === STATUS_REFUNDED) {
    return verdict(CLAWBACK_ALREADY_REFUNDED, "settlement is already refunded");
  }
  if (settlement.status !== STATUS_CREDITED) {
    return verdict(CLAWBACK_NOTHING_TO_TAKE, "settlement is not in credited state");
  }
  return null;
}

/**
 * Снимать ли выданное по этому токену.
 *
 * Порядок: запись расчёта (есть, чья, в каком состоянии) → ответ Play. Play обязан подтвердить,
 * что покупки в состоянии «куплена» больше нет, — либо не знать токена вовсе (возврат мог стереть
 * его из API). Всё остальное, включая нечитаемое состояние, — «Play не согласен»: не снимать по
 * догадке дешевле, чем отобрать у игрока оплаченное.
 *
 * @param {object|null} settlement запись `purchase_settlements/{id}`.
 * @param {object|null} purchase ответ Play, прочитанный `readProductPurchase`; null — Play токена
 *   не знает.
 * @param {string|null} uid кому снимать; сверяется с записью.
 * @param {number} now серверное время.
 */
function decideClawback({settlement, purchase, uid, now}) {
  const gate = settlementClawbackGate(settlement, uid);
  if (gate) return gate;

  if (purchase && purchase.purchaseState === PURCHASE_STATE_PURCHASED) {
    return verdict(CLAWBACK_PLAY_DISAGREES, "Play still reports the purchase as bought");
  }
  if (purchase && !Number.isInteger(purchase.purchaseState)) {
    return verdict(CLAWBACK_PLAY_DISAGREES, "Play did not say the purchase is no longer bought");
  }

  return {
    decision: CLAWBACK_CLAW_BACK,
    reason: purchase ? "Play no longer reports the purchase as bought" : "Play does not know this token",
    clawback: {
      uid: stringValue(settlement.uid),
      settlementId: stringValue(settlement.settlementId),
      sku: stringValue(settlement.sku),
      orderId: stringValue(settlement.orderId),
      // Сумма — из записи расчёта: столько было выдано, столько и отбирается. В уведомлении суммы
      // нет, и её появление там ничего бы не изменило — верить чужому числу о своих деньгах нельзя.
      goldGranted: wholeAtLeastZero(settlement.goldGranted),
      quantity: wholeAtLeastZero(settlement.quantity),
      goldPerUnit: wholeAtLeastZero(settlement.goldPerUnit),
      creditedConstantsVersion: wholeAtLeastZero(settlement.constantsVersion),
      isTest: settlement.isTest === true,
      purchaseState: purchase ? purchase.purchaseState : null,
      atMs: wholeAtLeastZero(now),
      ...(settlement.productKind === PRODUCT_PREMIUM ? {
        productKind: PRODUCT_PREMIUM,
        premiumGrantId: settlement.premiumGrantId,
        premiumGrantedMs: settlement.premiumGrantedMs,
        premiumDays: settlement.premiumDays,
      } : {}),
    },
  };
}

function verdict(decision, reason) {
  return {decision, reason};
}

/**
 * Поля, которыми запись расчёта помечается возвращённой.
 *
 * Слияние, а не замена: запись о начислении — доказательство того, что игроку выдали, и стирать её
 * ради отметки о возврате значит терять половину истории. Состояние меняется на «возвращено», и
 * `settlementGate` перестаёт отвечать повтором «начислено» — повторная проверка того же токена
 * получит отказ вместо разрешения погасить покупку, за которую деньги вернули.
 */
function refundSettlementUpdate({clawback, notificationId, goldBefore, goldAfter, shortfallAdded, now, premiumRevokedMs, premiumConsumedMs, premiumUntilMs}) {
  return {
    status: STATUS_REFUNDED,
    refundedAtMs: wholeAtLeastZero(now),
    refundNotificationId: stringValue(notificationId),
    goldTakenBack: Math.max(0, wholeAtLeastZero(goldBefore) - wholeAtLeastZero(goldAfter)),
    shortfallAdded: wholeAtLeastZero(shortfallAdded),
    goldBeforeRefund: wholeAtLeastZero(goldBefore),
    goldAfterRefund: wholeAtLeastZero(goldAfter),
    refundedPurchaseState: Number.isInteger(clawback && clawback.purchaseState) ? clawback.purchaseState : null,
    ...(clawback && clawback.productKind === PRODUCT_PREMIUM ? {premiumRevokedMs, premiumConsumedMs, premiumUntilMs} : {}),
  };
}

/**
 * Квитанция о возврате: ключ и параметры, не текст.
 *
 * Лежит рядом с квитанцией о начислении, а не поверх неё, и её сумма отрицательна — игрок видит
 * два движения, потому что их и было два. Недостача названа отдельно: «часть вернуть было уже не
 * с чего» — это не то же самое, что «вернули меньше».
 */
function refundReceiptRecord({clawback, notificationId, goldBefore, goldAfter, shortfallAdded, now, premiumRevokedMs, premiumConsumedMs, premiumUntilMs}) {
  if (clawback && clawback.productKind === PRODUCT_PREMIUM) return {
    receiptId: refundDocId(clawback.settlementId), uid: clawback.uid, kind: RECEIPT_KIND_PURCHASE_REFUNDED,
    messageKey: "receipt.premium_refunded",
    params: {sku: clawback.sku, premiumRevokedMs, premiumConsumedMs, premiumUntilMs},
    amount: -premiumRevokedMs, currency: "PREMIUM_MS", premiumGrantId: clawback.premiumGrantId,
    settlementId: clawback.settlementId, notificationId: stringValue(notificationId), createdAtMs: wholeAtLeastZero(now),
  };
  const takenBack = Math.max(0, wholeAtLeastZero(goldBefore) - wholeAtLeastZero(goldAfter));
  const id = refundDocId(clawback && clawback.settlementId);
  return {
    receiptId: id,
    uid: stringValue(clawback && clawback.uid),
    kind: RECEIPT_KIND_PURCHASE_REFUNDED,
    messageKey: RECEIPT_MESSAGE_GOLD_PACK_REFUNDED,
    params: {
      sku: stringValue(clawback && clawback.sku),
      gold: takenBack,
      shortfall: wholeAtLeastZero(shortfallAdded),
    },
    amount: -takenBack,
    currency: "GOLD",
    settlementId: stringValue(clawback && clawback.settlementId),
    notificationId: stringValue(notificationId),
    createdAtMs: wholeAtLeastZero(now),
  };
}

/**
 * Аудит возврата: кто, токен (его id), SKU, сумма, документ констант с версией.
 *
 * Версий две, и обе значат разное: под какой таблицей начисляли и какая действует сейчас. Смена
 * цены не ретроактивна — возврат отбирает выданное тогда, а не то, что стоил бы пак сегодня, — и
 * доказывается это только тем, что записаны обе.
 */
function refundAuditRecord({
  clawback, notificationId, goldBefore, goldAfter, shortfallAdded, shortfallAfter, constantsDoc, constantsVersion, now,
  premiumRevokedMs, premiumConsumedMs, premiumUntilMs,
}) {
  const id = refundDocId(clawback && clawback.settlementId);
  return {
    auditId: id,
    action: AUDIT_ACTION_PURCHASE_REFUND,
    uid: stringValue(clawback && clawback.uid),
    settlementId: stringValue(clawback && clawback.settlementId),
    notificationId: stringValue(notificationId),
    sku: stringValue(clawback && clawback.sku),
    orderId: stringValue(clawback && clawback.orderId),
    quantity: wholeAtLeastZero(clawback && clawback.quantity),
    goldPerUnit: wholeAtLeastZero(clawback && clawback.goldPerUnit),
    goldGranted: wholeAtLeastZero(clawback && clawback.goldGranted),
    goldTakenBack: Math.max(0, wholeAtLeastZero(goldBefore) - wholeAtLeastZero(goldAfter)),
    goldBefore: wholeAtLeastZero(goldBefore),
    goldAfter: wholeAtLeastZero(goldAfter),
    shortfallAdded: wholeAtLeastZero(shortfallAdded),
    shortfallAfter: wholeAtLeastZero(shortfallAfter),
    constantsDoc: stringValue(constantsDoc),
    constantsVersion: wholeAtLeastZero(constantsVersion),
    creditedConstantsVersion: wholeAtLeastZero(clawback && clawback.creditedConstantsVersion),
    isTest: clawback ? clawback.isTest === true : false,
    atMs: wholeAtLeastZero(now),
    ...(clawback && clawback.productKind === PRODUCT_PREMIUM ? {
      productKind: PRODUCT_PREMIUM, premiumGrantId: clawback.premiumGrantId,
      premiumGrantedMs: clawback.premiumGrantedMs, premiumRevokedMs, premiumConsumedMs, premiumUntilMs,
    } : {}),
  };
}

/**
 * Запись о сообщении: что приехало и чем кончилось.
 *
 * Пишется на каждое прочитанное сообщение, включая те, по которым ничего не двигалось, — иначе
 * «уведомление пришло, и ничего не произошло» неотличимо от «уведомление не приходило». Она же
 * отвечает на повторную доставку: результат берётся отсюда, и второй раз не пишется ничего.
 *
 * Сырого токена здесь нет — только его хеш, он же id записи расчёта: id документа видит всякий, кто
 * листает коллекцию, а токен — ключ к покупке.
 */
function notificationRecord({
  messageId, notification, kind, result, settlementId: id, sku, uid, publishTimeMs, now, detail,
}) {
  const voided = isObject(notification) ? notification.voidedPurchaseNotification : null;
  const oneTime = isObject(notification) ? notification.oneTimeProductNotification : null;
  return {
    messageId: stringValue(messageId),
    kind: stringValue(kind),
    result: stringValue(result),
    packageName: packageOf(notification),
    notificationType: isObject(oneTime) ? intOrNull(oneTime.notificationType) : null,
    refundType: isObject(voided) ? intOrNull(voided.refundType) : null,
    productType: isObject(voided) ? intOrNull(voided.productType) : null,
    settlementId: stringValue(id),
    sku: stringValue(sku),
    uid: stringValue(uid),
    eventTimeMs: isObject(notification) ? wholeAtLeastZero(notification.eventTimeMillis) : 0,
    publishTimeMs: wholeAtLeastZero(publishTimeMs),
    recordedAtMs: wholeAtLeastZero(now),
    detail: stringValue(detail),
  };
}

/** Id квитанции и аудита возврата — рядом с записями начисления, не поверх них. */
function refundDocId(settlementDocId) {
  return `${stringValue(settlementDocId)}${REFUND_DOC_SUFFIX}`;
}

function isObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function intOrNull(value) {
  if (value === null || value === undefined || value === "" || typeof value === "boolean") return null;
  const parsed = Number(value);
  return Number.isInteger(parsed) ? parsed : null;
}

function wholeAtLeastZero(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.trunc(parsed)) : 0;
}

function stringValue(value) {
  if (value === null || value === undefined) return "";
  return String(value);
}

module.exports = {
  AUDIT_ACTION_PURCHASE_REFUND,
  CLAWBACK_ALREADY_REFUNDED,
  CLAWBACK_CLAW_BACK,
  CLAWBACK_NOTHING_TO_TAKE,
  CLAWBACK_PLAY_DISAGREES,
  NOTIFICATION_PURCHASED,
  NOTIFICATION_TEST,
  NOTIFICATION_UNKNOWN,
  NOTIFICATION_VOID,
  ONE_TIME_PRODUCT_CANCELED,
  ONE_TIME_PRODUCT_PURCHASED,
  PURCHASE_NOTIFICATIONS_COLLECTION,
  RECEIPT_KIND_PURCHASE_REFUNDED,
  RECEIPT_MESSAGE_GOLD_PACK_REFUNDED,
  REFUND_DOC_SUFFIX,
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
};
