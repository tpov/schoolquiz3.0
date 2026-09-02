"use strict";

/**
 * Проверка чека Play: решение о начислении и записи, которые оно оставляет.
 *
 * Клиент уже умеет купить `gold_pack_*` через Play Billing, но покупка — это утверждение клиента,
 * а факт — ответ Play. Порядок поэтому нерушим: спросить Play → начислить → подтвердить клиенту →
 * клиент гасит покупку. Начисленное на устройстве подделал бы любой, кто правит запрос; погашенное
 * до начисления потеряло бы деньги игрока безвозвратно — погашенный токен Play не возвращает.
 *
 * Ключ идемпотентности — сам токен покупки: `purchase_settlements/{sha256(token)}`. Play доставляет
 * непогашенную покупку заново при каждом подключении, поэтому один токен приедет не раз, и каждый
 * повтор получает сохранённый ответ и не двигает ничего. Расчёт и изменение баланса — одна
 * транзакция; золото пишется абсолютом, потому что `increment` не отличает повтор от первой
 * доставки.
 *
 * Суммы — из серверной таблицы (`goldPacks` в `configs/economy`), которую клиент не видит; аудит
 * называет её версию, чтобы смена цены доказуемо не была задним числом.
 *
 * Модуль чистый — тестируется без firebase-admin: здесь решение, формы записей и чтение ответа
 * Play. Транзакция — purchase-settlement.js, запрос к Play — play-developer-api.js, вызов —
 * index.js.
 */

const crypto = require("crypto");

/** Расчёты по токенам: один документ на токен, ключ — sha256 токена. */
const PURCHASE_SETTLEMENTS_COLLECTION = "purchase_settlements";
/** Аудит движений денег: кто, токен, SKU, сумма, документ констант с версией. */
const PURCHASE_AUDIT_COLLECTION = "purchase_audit";
/** Квитанции под пользователем: `users/{uid}/receipts/{settlementId}`. */
const RECEIPTS_SUBCOLLECTION = "receipts";

/** Ответы клиенту. PENDING — не ошибка: дорешается позже тем же путём. */
const STATUS_CREDITED = "CREDITED";
const STATUS_PENDING = "PENDING";

/** Что делать с пришедшим чеком. */
const SETTLEMENT_CREDIT = "credit";
const SETTLEMENT_REPLAY = "replay";
const SETTLEMENT_PENDING = "pending";
const SETTLEMENT_REFUSE = "refuse";

/** Состояния покупки у Play (`purchases.products`, поле `purchaseState`). */
const PURCHASE_STATE_PURCHASED = 0;
const PURCHASE_STATE_CANCELED = 1;
const PURCHASE_STATE_PENDING = 2;
/** `consumptionState`: 0 — ещё не погашена, 1 — погашена. Ничего третьего Play не обещает. */
const CONSUMPTION_STATE_UNCONSUMED = 0;
const CONSUMPTION_STATE_CONSUMED = 1;
/** `purchaseType`: 0 — тестовая покупка лицензионного тестера. У обычной покупки поля нет. */
const PURCHASE_TYPE_TEST = 0;

/**
 * Исходы запроса к Play. Словарь объявлен здесь, и play-developer-api.js говорит им же.
 *
 * Три, а не код HTTP: решение принимается по типу исхода, не по числу. NOT_FOUND — Play токена не
 * знает (404, или 400 с причиной о токене: подделка, чужой пакет, чужой SKU) — окончательный
 * отказ. UNAVAILABLE — ответа о токене нет (сеть, 5xx, отказ в доступе, 400 о форме запроса) — не
 * вердикт: клиент повторит позже тем же путём, и покупка до тех пор остаётся непогашенной.
 */
const PLAY_OUTCOME_FOUND = "found";
const PLAY_OUTCOME_NOT_FOUND = "not_found";
const PLAY_OUTCOME_UNAVAILABLE = "unavailable";

/**
 * Машинные коды отказа.
 *
 * Код HTTPS (`failed-precondition`, `permission-denied`, `invalid-argument`) говорит клиенту, что
 * делать; код причины говорит человеку и аналитике, что случилось, — без разбора текста (AD-15).
 * Уезжает в `details.reasonCode` ошибки; текст рядом с ним — для логов, не для ветвления.
 */
const REASON_TOKEN_UNKNOWN = "TOKEN_UNKNOWN";
const REASON_SKU_MISMATCH = "SKU_MISMATCH";
const REASON_PURCHASE_CANCELED = "PURCHASE_CANCELED";
const REASON_PURCHASE_STATE_UNKNOWN = "PURCHASE_STATE_UNKNOWN";
const REASON_CONSUMED_WITHOUT_SETTLEMENT = "CONSUMED_WITHOUT_SETTLEMENT";
const REASON_SKU_NOT_SOLD = "SKU_NOT_SOLD";
const REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT = "TOKEN_OWNED_BY_ANOTHER_ACCOUNT";
const REASON_SETTLEMENT_NOT_CREDITED = "SETTLEMENT_NOT_CREDITED";
const REASON_CONSUMPTION_STATE_UNKNOWN = "CONSUMPTION_STATE_UNKNOWN";
const REASON_QUANTITY_UNREADABLE = "QUANTITY_UNREADABLE";
/** Запрос не той формы — отказ ещё до Play (purchase-settlement.js). */
const REASON_REQUEST_INVALID = "REQUEST_INVALID";
const REASON_CODES = Object.freeze([
  REASON_TOKEN_UNKNOWN,
  REASON_SKU_MISMATCH,
  REASON_PURCHASE_CANCELED,
  REASON_PURCHASE_STATE_UNKNOWN,
  REASON_CONSUMED_WITHOUT_SETTLEMENT,
  REASON_SKU_NOT_SOLD,
  REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT,
  REASON_SETTLEMENT_NOT_CREDITED,
  REASON_CONSUMPTION_STATE_UNKNOWN,
  REASON_QUANTITY_UNREADABLE,
  REASON_REQUEST_INVALID,
]);

/** Вид квитанции и ключ её текста: текст собирает устройство по ключу и параметрам, не сервер. */
const RECEIPT_KIND_PURCHASE_CREDITED = "PURCHASE_CREDITED";
const RECEIPT_MESSAGE_GOLD_PACK_CREDITED = "receipt.gold_pack_credited";
/** Действие в аудите. Возврат (история 1.3) заведёт своё, тем же порядком. */
const AUDIT_ACTION_PURCHASE_CREDIT = "PURCHASE_CREDIT";

/**
 * Потолок длины токена. Токен уезжает в путь запроса к Play и в хеш, поэтому его содержимое
 * безразлично — но не размер: хешировать мегабайт на каждой попытке значит платить за него.
 */
const MAX_TOKEN_LENGTH = 2048;
/**
 * Форма SKU у Play: строчные буквы, цифры, подчёркивание и точка; первым — буква или цифра.
 * Одно определение на оба модуля: economy-constants.js читает таблицу паков по нему же.
 */
const SKU_PATTERN = /^[a-z0-9][a-z0-9_.]{0,127}$/;

/**
 * Ключ расчёта — sha256 токена.
 *
 * Сам токен в id документа не годится: Play не обещает его длины и алфавита, а id обязан быть
 * коротким и без косой черты. Хеш детерминирован — тот же токен всегда ведёт к тому же документу,
 * что и делает его ключом идемпотентности, — и не раскрывает токен тому, кто увидит id.
 */
function settlementId(purchaseToken) {
  return sha256Hex(purchaseToken);
}

/**
 * Кем покупка помечена у Play: sha256 uid в `obfuscatedExternalAccountId`.
 *
 * Клиент (история 1.2) кладёт это значение в покупку, Play возвращает его как есть. Если метка
 * есть и не совпадает с тем, кто предъявляет чек, — платил другой. Хеш, а не uid: метку Play видит
 * в своих отчётах, а uid — не для отчётов Play. Клиент зеркалит ровно эту функцию.
 */
function accountIdFor(uid) {
  return sha256Hex(uid);
}

function sha256Hex(value) {
  return crypto.createHash("sha256").update(stringValue(value)).digest("hex");
}

/**
 * Проверяет форму запроса до того, как спрашивать Play.
 *
 * Строки, а не «что-то приводимое к строке»: объект вместо токена — это не токен, а `String({})`
 * в хеше дал бы всем таким запросам один и тот же ключ расчёта.
 *
 * @returns {{valid: boolean, purchaseToken: string, sku: string, reason: string}}
 */
function validatePurchaseRequest(data) {
  const purchaseToken = data && data.purchaseToken;
  const sku = data && data.sku;

  if (typeof purchaseToken !== "string" || !purchaseToken) {
    return invalid("purchaseToken must be a non-empty string");
  }
  if (purchaseToken.length > MAX_TOKEN_LENGTH) return invalid("purchaseToken is too long");
  if (typeof sku !== "string" || !sku) return invalid("sku must be a non-empty string");
  if (!SKU_PATTERN.test(sku)) return invalid("sku has invalid characters");

  return {valid: true, purchaseToken, sku, reason: "ok"};
}

function invalid(reason) {
  return {valid: false, purchaseToken: "", sku: "", reason};
}

/**
 * Читает ответ Play `purchases.products.get` в рабочую форму.
 *
 * Play отдаёт int64 строками (`purchaseTimeMillis`), необязательные поля опускает (`quantity`,
 * `purchaseType`) и меняет форму между версиями. Здесь всё приводится один раз, чтобы решение
 * сравнивало числа с числами, а не строки с числами.
 *
 * Отсутствующее поле и испорченное поле — разные вещи. Нет `quantity` — Play обещает одну штуку;
 * есть, но не целое от единицы — `null`, и решение откажет, а не начислит одну. То же с
 * `consumptionState`: нет — не погашена; есть, но не читается — `null`, отказ.
 *
 * @returns {object|null} null, если ответ — не объект.
 */
function readProductPurchase(apiJson) {
  if (!apiJson || typeof apiJson !== "object" || Array.isArray(apiJson)) return null;
  const purchaseType = intOrNull(apiJson.purchaseType);
  return {
    productId: stringValue(apiJson.productId),
    orderId: stringValue(apiJson.orderId),
    purchaseState: intOrNull(apiJson.purchaseState),
    consumptionState: isAbsent(apiJson.consumptionState) ?
      CONSUMPTION_STATE_UNCONSUMED : intOrNull(apiJson.consumptionState),
    purchaseType,
    quantity: isAbsent(apiJson.quantity) ? 1 : positiveIntOrNull(apiJson.quantity),
    purchaseTimeMs: intOrNull(apiJson.purchaseTimeMillis) ?? 0,
    acknowledgementState: intOrNull(apiJson.acknowledgementState),
    regionCode: stringValue(apiJson.regionCode),
    obfuscatedExternalAccountId: stringValue(apiJson.obfuscatedExternalAccountId),
    isTest: purchaseType === PURCHASE_TYPE_TEST,
  };
}

/**
 * Что исход запроса к Play значит для решения.
 *
 * Найдено — покупка, прочитанная {@link readProductPurchase}. Не найдено — `null`, и решение
 * откажет как подделке. Недоступно — не вердикт: ничего не пишется, клиент повторит.
 *
 * Найдено, но тело не читается, — тоже «недоступно», а не подделка: Play ответил, значит, токен
 * настоящий, а не та форма ответа — наша беда, и повторить позже безопаснее, чем отказать.
 * Незнакомый исход приравнивается к недоступности по той же причине.
 *
 * @returns {{unavailable: boolean, purchase: object|null}}
 */
function purchaseFromLookup(lookup) {
  const outcome = lookup && lookup.outcome;
  if (outcome === PLAY_OUTCOME_FOUND) {
    const purchase = readProductPurchase(lookup.purchase);
    return purchase ? {unavailable: false, purchase} : {unavailable: true, purchase: null};
  }
  if (outcome === PLAY_OUTCOME_NOT_FOUND) return {unavailable: false, purchase: null};
  return {unavailable: true, purchase: null};
}

/**
 * Вердикт по уже существующей записи расчёта — до того, как спрашивать Play.
 *
 * Порядок тот же, что у ключей очереди (`gateVerdict` в mutation-queue.js): сначала владелец,
 * потом состояние. Чужой токен — не повтор, а попытка прочитать чужой результат, и ответа он не
 * получает. Свой и начисленный — повтор: сохранённый ответ возвращается как есть, сколько бы раз
 * его ни спросили, — в том числе после того, как клиент погасил покупку и Play стал отвечать
 * «погашена». Свой, но не в состоянии «начислено» (возврат, история 1.3), — отказ: повторять
 * ответ «начислено» по возвращённой покупке значило бы дать погасить то, за что деньги уже
 * вернули.
 *
 * @returns {object|null} null, если записи нет и решать должен полный разбор.
 */
function settlementGate(existing, uid) {
  if (!existing) return null;
  if (stringValue(existing.uid) !== stringValue(uid)) {
    return refuse(
      "permission-denied", "purchase token belongs to another account", REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT,
    );
  }
  if (existing.status !== STATUS_CREDITED) {
    return refuse("failed-precondition", "settlement is not in credited state", REASON_SETTLEMENT_NOT_CREDITED);
  }
  return {decision: SETTLEMENT_REPLAY, reason: "already settled", response: replayResponse(existing)};
}

/**
 * Решение по чеку: начислить, повторить сохранённое, подождать или отказать.
 *
 * Проверки идут в порядке спецификации и отказывают на первой неудаче: запись расчёта (владелец,
 * повтор) → Play знает токен → SKU совпадает с заявленным → покупатель, если Play его назвал →
 * состояние покупки → не погашена мимо нас → количество читается → SKU продаётся. Сумма берётся
 * из таблицы, а не из запроса: клиент, называющий цену, назовёт любую.
 *
 * @param {object|null} existing запись `purchase_settlements/{id}`, если она есть.
 * @param {object|null} purchase ответ Play, прочитанный {@link readProductPurchase}; null — Play
 *   токена не знает (подделка или чужой пакет).
 * @param {string} claimedSku SKU, который назвал клиент.
 * @param {string} uid кому начислять.
 * @param {object} constants таблица, приведённая `readEconomyConstants`.
 */
function decideSettlement({existing, purchase, claimedSku, uid, constants}) {
  const gate = settlementGate(existing, uid);
  if (gate) return gate;

  const sku = stringValue(claimedSku);
  if (!purchase) {
    return refuse("permission-denied", "Play does not recognise this purchase token", REASON_TOKEN_UNKNOWN);
  }
  if (purchase.productId !== sku) {
    return refuse("invalid-argument", "sku does not match the purchase", REASON_SKU_MISMATCH);
  }
  // Play назвал покупателя — и это не тот, кто предъявляет чек. Метки нет у покупок, сделанных до
  // того, как клиент начал её ставить; они проходят по одному только токену.
  if (purchase.obfuscatedExternalAccountId && purchase.obfuscatedExternalAccountId !== accountIdFor(uid)) {
    return refuse("permission-denied", "purchase was made by another account", REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT);
  }

  switch (purchase.purchaseState) {
    case PURCHASE_STATE_PENDING:
      return {decision: SETTLEMENT_PENDING, reason: "payment pending", response: pendingResponse(sku)};
    case PURCHASE_STATE_CANCELED:
      return refuse("failed-precondition", "purchase was canceled", REASON_PURCHASE_CANCELED);
    case PURCHASE_STATE_PURCHASED:
      break;
    default:
      return refuse("failed-precondition", "purchase state is unknown", REASON_PURCHASE_STATE_UNKNOWN);
  }

  // Погашенный токен без записи расчёта — не наш: погашение делает клиент после подтверждения
  // сервера, и если подтверждения не было, кто-то погасил мимо этого пути. Начислять по такому
  // значит начислять по чеку, который никогда не пройдёт проверку второй раз.
  if (purchase.consumptionState === CONSUMPTION_STATE_CONSUMED) {
    return refuse(
      "failed-precondition", "purchase was consumed without a settlement", REASON_CONSUMED_WITHOUT_SETTLEMENT,
    );
  }
  if (purchase.consumptionState !== CONSUMPTION_STATE_UNCONSUMED) {
    return refuse("failed-precondition", "purchase consumption state is unknown", REASON_CONSUMPTION_STATE_UNKNOWN);
  }
  if (purchase.quantity === null) {
    return refuse("failed-precondition", "purchase quantity is unreadable", REASON_QUANTITY_UNREADABLE);
  }

  const goldPerUnit = goldPackAmount(constants, sku);
  if (goldPerUnit === null) return refuse("invalid-argument", `sku ${sku} is not sold`, REASON_SKU_NOT_SOLD);

  return {
    decision: SETTLEMENT_CREDIT,
    reason: "verified",
    credit: {
      sku,
      goldPerUnit,
      quantity: purchase.quantity,
      goldGranted: goldPerUnit * purchase.quantity,
      isTest: purchase.isTest,
      constantsVersion: safeNumber(constants && constants.version, 0),
    },
  };
}

/**
 * Сколько золота за единицу этого SKU по таблице. null — не продаётся.
 *
 * Ноль — тоже «не продаётся»: клиенту нельзя ответить «начислено» за ничего, а таблица нулевых
 * паков не назначает (economy-constants.js откатывает ноль к начальному размеру). Премиум и
 * коробки (эпики 2 и 3) в `goldPacks` не значатся и отклоняются как непродаваемые: грант зависит
 * от SKU, а не зашит под золото, и когда они появятся — появятся в своей таблице.
 */
function goldPackAmount(constants, sku) {
  const packs = constants && constants.goldPacks;
  if (!packs || typeof packs !== "object" || !Object.prototype.hasOwnProperty.call(packs, sku)) {
    return null;
  }
  const amount = packs[sku];
  return Number.isInteger(amount) && amount >= 1 ? amount : null;
}

function refuse(code, reason, reasonCode) {
  return {decision: SETTLEMENT_REFUSE, code, reason, reasonCode};
}

/** Ответ на начисление. Сохраняется в записи расчёта и возвращается на повтор без изменений. */
function successResponse({sku, goldGranted, gold, settlementId: id}) {
  return {
    status: STATUS_CREDITED,
    sku: stringValue(sku),
    goldGranted: safeNumber(goldGranted, 0),
    gold: safeNumber(gold, 0),
    settlementId: stringValue(id),
  };
}

/** Ответ на ожидание оплаты. Не ошибка и не начисление: клиент не гасит и повторит позже. */
function pendingResponse(sku) {
  return {status: STATUS_PENDING, sku: stringValue(sku)};
}

/**
 * Что вернуть на повтор. Сохранённый ответ как есть — с тем балансом, что был в момент
 * начисления, а не с текущим: повтор возвращает тот же ответ, а не новый. Запись без ответа
 * (правленная руками) собирается из её же полей — игрок должен получить ответ, по которому сможет
 * погасить покупку.
 */
function replayResponse(existing) {
  if (existing.response && typeof existing.response === "object") return existing.response;
  return successResponse({
    sku: existing.sku,
    goldGranted: existing.goldGranted,
    gold: existing.goldAfter,
    settlementId: existing.settlementId,
  });
}

/**
 * Запись расчёта: доказательство, что токен рассчитан, и ответ, который вернётся на повтор.
 *
 * Сам токен не хранится — id и есть его хеш, а возврат (история 1.3) получает токен от Play и
 * хеширует его так же. `orderId` — для поддержки и сверки с Play Console.
 */
function settlementRecord({id, uid, purchase, credit, goldBefore, goldAfter, constantsDoc, now, response}) {
  return {
    settlementId: stringValue(id),
    uid: stringValue(uid),
    status: STATUS_CREDITED,
    sku: stringValue(credit.sku),
    orderId: stringValue(purchase && purchase.orderId),
    purchaseTimeMs: safeNumber(purchase && purchase.purchaseTimeMs, 0),
    quantity: safeNumber(credit.quantity, 1),
    goldPerUnit: safeNumber(credit.goldPerUnit, 0),
    goldGranted: safeNumber(credit.goldGranted, 0),
    goldBefore: safeNumber(goldBefore, 0),
    goldAfter: safeNumber(goldAfter, 0),
    constantsDoc: stringValue(constantsDoc),
    constantsVersion: safeNumber(credit.constantsVersion, 0),
    isTest: credit.isTest === true,
    settledAtMs: safeNumber(now, 0),
    response: response === undefined ? null : response,
  };
}

/**
 * Квитанция игроку: ключ и параметры, не текст. Текст собирает устройство на своём языке —
 * украинский интерфейс обязателен по закону, и сервер языка игрока не знает.
 */
function receiptRecord({id, uid, credit, now}) {
  return {
    receiptId: stringValue(id),
    uid: stringValue(uid),
    kind: RECEIPT_KIND_PURCHASE_CREDITED,
    messageKey: RECEIPT_MESSAGE_GOLD_PACK_CREDITED,
    params: {
      sku: stringValue(credit.sku),
      gold: safeNumber(credit.goldGranted, 0),
      quantity: safeNumber(credit.quantity, 1),
    },
    amount: safeNumber(credit.goldGranted, 0),
    currency: "GOLD",
    settlementId: stringValue(id),
    createdAtMs: safeNumber(now, 0),
  };
}

/**
 * Аудит: кто, токен (его id), SKU, сумма, документ констант с версией.
 *
 * Версия — то, ради чего запись существует: смена цены не ретроактивна, и без записи о том, под
 * какой таблицей принималось решение, доказать это потом нечем.
 */
function auditRecord({id, uid, purchase, credit, goldBefore, goldAfter, constantsDoc, now}) {
  return {
    auditId: stringValue(id),
    action: AUDIT_ACTION_PURCHASE_CREDIT,
    uid: stringValue(uid),
    settlementId: stringValue(id),
    sku: stringValue(credit.sku),
    orderId: stringValue(purchase && purchase.orderId),
    quantity: safeNumber(credit.quantity, 1),
    goldPerUnit: safeNumber(credit.goldPerUnit, 0),
    goldGranted: safeNumber(credit.goldGranted, 0),
    goldBefore: safeNumber(goldBefore, 0),
    goldAfter: safeNumber(goldAfter, 0),
    constantsDoc: stringValue(constantsDoc),
    constantsVersion: safeNumber(credit.constantsVersion, 0),
    isTest: credit.isTest === true,
    atMs: safeNumber(now, 0),
  };
}

function isAbsent(value) {
  return value === null || value === undefined;
}

function intOrNull(value) {
  if (isAbsent(value) || value === "" || typeof value === "boolean") return null;
  const parsed = Number(value);
  return Number.isInteger(parsed) ? parsed : null;
}

function positiveIntOrNull(value) {
  const parsed = intOrNull(value);
  return parsed !== null && parsed >= 1 ? parsed : null;
}

function safeNumber(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function stringValue(value) {
  if (isAbsent(value)) return "";
  return String(value);
}

module.exports = {
  AUDIT_ACTION_PURCHASE_CREDIT,
  MAX_TOKEN_LENGTH,
  PLAY_OUTCOME_FOUND,
  PLAY_OUTCOME_NOT_FOUND,
  PLAY_OUTCOME_UNAVAILABLE,
  PURCHASE_AUDIT_COLLECTION,
  PURCHASE_SETTLEMENTS_COLLECTION,
  REASON_CODES,
  REASON_CONSUMED_WITHOUT_SETTLEMENT,
  REASON_CONSUMPTION_STATE_UNKNOWN,
  REASON_PURCHASE_CANCELED,
  REASON_PURCHASE_STATE_UNKNOWN,
  REASON_QUANTITY_UNREADABLE,
  REASON_REQUEST_INVALID,
  REASON_SETTLEMENT_NOT_CREDITED,
  REASON_SKU_MISMATCH,
  REASON_SKU_NOT_SOLD,
  REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT,
  REASON_TOKEN_UNKNOWN,
  RECEIPTS_SUBCOLLECTION,
  RECEIPT_KIND_PURCHASE_CREDITED,
  RECEIPT_MESSAGE_GOLD_PACK_CREDITED,
  SETTLEMENT_CREDIT,
  SETTLEMENT_PENDING,
  SETTLEMENT_REFUSE,
  SETTLEMENT_REPLAY,
  SKU_PATTERN,
  STATUS_CREDITED,
  STATUS_PENDING,
  accountIdFor,
  auditRecord,
  decideSettlement,
  pendingResponse,
  purchaseFromLookup,
  readProductPurchase,
  receiptRecord,
  settlementGate,
  settlementId,
  settlementRecord,
  successResponse,
  validatePurchaseRequest,
};
