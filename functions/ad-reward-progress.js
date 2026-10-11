"use strict";

/**
 * Путь подтверждения: от сырого callback'а до записей и прогресса, исполняемый без firebase-admin.
 *
 * `index.js` остаётся обвязкой: запрос, fetcher ключей, ответ. Всё между — здесь, Firestore
 * приходит параметром, и тесты подделкой проверяют само тело транзакции. Порядок: разбор →
 * выбор ключа → подпись → одна транзакция: guard (повтор), запись callback'а, прогресс игрока.
 * Прогресс пишется абсолютом: `increment` не отличает повтор от первого просмотра (NFR2).
 *
 * Решает подпись, а не происхождение запроса: endpoint открыт `invoker: "public"`, потому что
 * вызывает Google, — но без честной подписи ни одна строка не двигается.
 */

const {
  AD_BOXES_PER_DAY,
  AD_BOX_CLOCK_FIELD,
  AD_VIEWS_PER_BOX,
  DAY_MS,
} = require("./gift-boxes");
const {CURRENCY, LEDGER_PATH, REASON, ledgerEntry} = require("./currency-ledger");
const {
  AD_REWARD_CONFIRMED,
  AD_REWARD_FORGED,
  AD_REWARD_FOREIGN,
  AD_REWARD_KEY_UNKNOWN,
  AD_REWARD_READY,
  AD_REWARD_REPLAYED,
  AD_REWARD_SIGNATURE_INVALID,
  AD_REWARD_UNUSABLE,
  AD_REWARD_UNVERIFIABLE,
  REASON_UNKNOWN_ACCOUNT,
  REASON_UNPARSEABLE,
  adRewardCallbackId,
  adRewardCallbackRecord,
  adRewardConfirmationId,
  adRewardConfirmationRecord,
  isValidRewardUid,
  readAdRewardCallback,
  selectVerifierKey,
  verifyAdRewardSignature,
} = require("./ad-reward-verification");

const AD_REWARD_CONFIRMATIONS_COLLECTION = "ad_reward_confirmations";
const AD_REWARD_CALLBACKS_COLLECTION = "ad_reward_callbacks";
/** Монотонный счётчик засчитанных просмотров на документе игрока; выдача коробки — история 3.2. */
const AD_REWARD_VIEWS_FIELD = "adRewardViews";

/** Исходы вызова: `ack` — Google получил ответ и не повторит, иначе ответ 500. */
const AD_REWARD_OUTCOME_ACKNOWLEDGED = "acknowledged";
const AD_REWARD_OUTCOME_UNAVAILABLE = "unavailable";

function fail(code, message) { const error = new Error(message); error.code = code; throw error; }

function exactNonNegative(value, label) {
  if (!Number.isSafeInteger(value) || value < 0) fail("INVALID_ARGUMENT", `${label} must be an exact nonnegative integer`);
  return value;
}

/**
 * Чистый план выдачи рекламной коробки (история 3.2).
 *
 * Коробка стоит `AD_VIEWS_PER_BOX` засчитанных просмотров и выдаётся на пересечении кратного
 * пяти — других моментов у неё нет. Суточный потолок — `AD_BOXES_PER_DAY` по собственным часам:
 * выдача не раньше `nextAdBoxAtMs`, катящиеся сутки от последней выдачи, как у премиального
 * бонуса. Пересечение, пришедшее в занятые сутки, не сгорает: просмотры остались в счётчике,
 * следующее пересечение попробует снова.
 */
function planAdRewardBox({views, nextAdBoxAtMs, nowMs}) {
  const at = exactNonNegative(nowMs, "server time");
  if (!Number.isSafeInteger(views) || views <= 0 || views % AD_VIEWS_PER_BOX !== 0) {
    return {issueBox: false, nextAdBoxAtMs: exactNonNegative(nextAdBoxAtMs || 0, AD_BOX_CLOCK_FIELD)};
  }
  if (at < exactNonNegative(nextAdBoxAtMs || 0, AD_BOX_CLOCK_FIELD)) {
    return {issueBox: false, nextAdBoxAtMs: nextAdBoxAtMs};
  }
  return {issueBox: true, nextAdBoxAtMs: at + AD_BOXES_PER_DAY * DAY_MS};
}

/**
 * Ключ подписи для callback'а: из кэша, а при незнакомом `key_id` — один принудительный забор.
 *
 * Ротация ключей объявляет себя незнакомым id; единственное честное продолжение — свежий список.
 * Fetcher дважды не ответил или ключ так и не нашёлся: у первого — исход UNAVAILABLE (ничего
 * не записано, Google повторит), у второй — UNVERIFIABLE (запись есть, прогресса нет).
 */
async function verifierKeyPem({callback, fetchKeys}) {
  const keyId = callback.params.keyId;
  let pem = selectVerifierKey(await fetchKeys(false), keyId);
  if (pem) return pem;
  pem = selectVerifierKey(await fetchKeys(true), keyId);
  return pem || null;
}

/**
 * Транзакция подтверждения: guard решает, что делает этот `transaction_id` — первый просмотр,
 * повтор того же игрока или чужой предъявитель той же транзакции.
 *
 * Запись callback'а merge-ится: повтор доставки того же сообщения переписывает строку своим
 * вердиктом — последняя доставка и есть показание. Guard создаётся ровно один раз (`create`):
 * параллельный вызов, рассчитавший транзакцию первым, роняет транзакцию — явный повтор читает
 * guard и отвечает исходом, не переписывая его. Аккаунта нет — credited быть нечему: строка
 * UNUSABLE с причиной, guard не тратится (ack уже ушёл, повторов этот callback не создаст).
 */
async function confirmAdReward({db, callback, now}) {
  const uid = callback.params.userId;
  const guardRef = db.collection(AD_REWARD_CONFIRMATIONS_COLLECTION).doc(adRewardConfirmationId(callback.params.transactionId));
  const userRef = db.collection("users").doc(uid);
  const callbackRef = db.collection(AD_REWARD_CALLBACKS_COLLECTION).doc(adRewardCallbackId(callback));
  for (let pass = 0; pass < 2; pass++) {
    let result = null;
    try {
      await db.runTransaction(async (transaction) => {
        const [guardSnapshot, userSnapshot] = await transaction.getAll(guardRef, userRef);
        const guard = guardSnapshot.exists ? guardSnapshot.data() : null;
        if (guard) {
          const verdict = guard.userId === uid ? AD_REWARD_REPLAYED : AD_REWARD_FOREIGN;
          transaction.set(callbackRef, adRewardCallbackRecord({callback, verdict, nowMs: now}), {merge: true});
          result = {verdict, reason: null};
          return;
        }
        if (!userSnapshot.exists) {
          transaction.set(callbackRef,
            adRewardCallbackRecord({callback, verdict: AD_REWARD_UNUSABLE, reason: REASON_UNKNOWN_ACCOUNT, nowMs: now}), {merge: true});
          result = {verdict: AD_REWARD_UNUSABLE, reason: REASON_UNKNOWN_ACCOUNT};
          return;
        }
        const stored = userSnapshot.exists ? userSnapshot.data() || {} : {};
        const views = exactNonNegative(Math.floor(Number(stored[AD_REWARD_VIEWS_FIELD] || 0)), AD_REWARD_VIEWS_FIELD);
        const countedViews = views + 1;
        // Выдача и её потолок — в той же транзакции, что и подтверждение: обойти часы отдельным
        // вызовом нельзя, их нет вне этой транзакции.
        const plan = planAdRewardBox({views: countedViews, nextAdBoxAtMs: stored[AD_BOX_CLOCK_FIELD], nowMs: now});
        const userPatch = {uid, [AD_REWARD_VIEWS_FIELD]: countedViews, updatedAtMs: now};
        if (plan.issueBox) {
          const boxCount = exactNonNegative(Math.floor(Number(stored.boxCount === undefined || stored.boxCount === null ? 0 : stored.boxCount)), "boxCount");
          userPatch.boxCount = boxCount + 1;
          userPatch[AD_BOX_CLOCK_FIELD] = plan.nextAdBoxAtMs;
        }
        transaction.create(guardRef, adRewardConfirmationRecord({callback, nowMs: now}));
        transaction.set(callbackRef, adRewardCallbackRecord({callback, verdict: AD_REWARD_CONFIRMED, nowMs: now}), {merge: true});
        transaction.set(userRef, userPatch, {merge: true});
        if (plan.issueBox) {
          // Движение коробок видно книге, как и всякое другое (box-service.js делает то же).
          const entry = ledgerEntry({
            uid,
            currency: CURRENCY.BOXES,
            delta: 1,
            reason: REASON.GIFT_BOX,
            sourceId: `${adRewardConfirmationId(callback.params.transactionId)}_ad`,
            atMs: now,
            details: {source: "AD", views: countedViews},
          });
          if (entry) transaction.set(db.doc(`${LEDGER_PATH}/${entry.id}`), entry, {merge: true});
        }
        result = {verdict: AD_REWARD_CONFIRMED, reason: null};
      });
    } catch (error) {
      // Гонка повторов: guard успел создать кто-то другой. Второй проход читает его и отвечает.
      if (pass === 0 && String(error && error.message).includes("ALREADY_EXISTS")) continue;
      throw error;
    }
    return result;
  }
  fail("UNAVAILABLE", "Ad reward confirmation did not settle; retry");
}

/**
 * Весь вызов: сырая query-строка → исход с вердиктом.
 *
 * Нечитаемое и непригодное попадает в запись с названной причиной и отвечается 200: Google
 * повторяет не-200 пять раз, а повторы мусора и подделок не нужны. Доступность ключей — единственный
 * исход без записи: вердикта нет, и Google повторит уже со свежим списком.
 *
 * @param {object} deps
 * @param {object} deps.db Firestore.
 * @param {string} deps.rawQuery query-строка запроса без «?».
 * @param {function(boolean): Promise<object|null>} deps.fetchKeys документ ключей сети; аргумент —
 *   принудительное обновление кэша.
 * @param {number} deps.now серверное время.
 * @param {object} deps.log логгер с `warn`.
 * @returns {Promise<{outcome: string, verdict: string, reason: ?string}>}
 */
async function applyAdReward({db, rawQuery, fetchKeys, now, log}) {
  const callback = readAdRewardCallback(rawQuery) ||
    {content: String(rawQuery || ""), params: null, unusableReason: REASON_UNPARSEABLE};
  if (callback.unusableReason) {
    await db.collection(AD_REWARD_CALLBACKS_COLLECTION)
      .doc(adRewardCallbackId(callback))
      .set(adRewardCallbackRecord({callback, verdict: AD_REWARD_UNUSABLE, reason: callback.unusableReason, nowMs: now}), {merge: true});
    return {outcome: AD_REWARD_OUTCOME_ACKNOWLEDGED, verdict: AD_REWARD_UNUSABLE, reason: callback.unusableReason};
  }
  let keyPem = null;
  try {
    keyPem = await verifierKeyPem({callback, fetchKeys});
  } catch (error) {
    log.warn("adMobRewardConfirmed: verifier keys unreachable; nothing was written", {message: String(error && error.message)});
    return {outcome: AD_REWARD_OUTCOME_UNAVAILABLE, verdict: AD_REWARD_KEY_UNKNOWN, reason: null};
  }
  if (!keyPem) {
    await db.collection(AD_REWARD_CALLBACKS_COLLECTION)
      .doc(adRewardCallbackId(callback))
      .set(adRewardCallbackRecord({callback, verdict: AD_REWARD_UNVERIFIABLE, nowMs: now}), {merge: true});
    return {outcome: AD_REWARD_OUTCOME_ACKNOWLEDGED, verdict: AD_REWARD_UNVERIFIABLE, reason: null};
  }
  if (!verifyAdRewardSignature({content: callback.content, signatureBase64: callback.params.signature, publicKeyPem: keyPem})) {
    log.warn("adMobRewardConfirmed: forged ad reward callback refused", {
      transactionId: callback.params.transactionId, userId: isValidRewardUid(callback.params.userId) ? callback.params.userId : null,
    });
    await db.collection(AD_REWARD_CALLBACKS_COLLECTION)
      .doc(adRewardCallbackId(callback))
      .set(adRewardCallbackRecord({callback, verdict: AD_REWARD_FORGED, nowMs: now}), {merge: true});
    return {outcome: AD_REWARD_OUTCOME_ACKNOWLEDGED, verdict: AD_REWARD_FORGED, reason: null};
  }
  const {verdict, reason} = await confirmAdReward({db, callback, now});
  return {outcome: AD_REWARD_OUTCOME_ACKNOWLEDGED, verdict, reason};
}

module.exports = {
  AD_REWARD_CALLBACKS_COLLECTION,
  AD_REWARD_CONFIRMATIONS_COLLECTION,
  AD_REWARD_OUTCOME_ACKNOWLEDGED,
  AD_REWARD_OUTCOME_UNAVAILABLE,
  AD_REWARD_VIEWS_FIELD,
  applyAdReward,
  planAdRewardBox,
};
