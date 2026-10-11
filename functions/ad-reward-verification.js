"use strict";

/**
 * Верификация SSV-callback рекламной сети (история 3.1).
 *
 * Засчитанный просмотр существует только как подписанное подтверждение, пришедшее от серверов
 * Google мимо телефона (NFR6): событие с устройства — не участник расчёта, и клиентского пути
 * «реклама просмотрена» на сервере нет вовсе. Контракт подписи — документированный SSV: ECDSA
 * P-256 с SHA-256 (DER, Base64) над сырым куском query-строки до `&signature=`, порядок параметров
 * не меняется; публичные ключи живёт списком на gstatic (`verifier-keys.json`), нужный выбирается
 * по `key_id` из callback'а, кэш ключей — не дольше суток.
 *
 * Чистый модуль: без firebase-admin и сети. Сеть приносит wiring — документ ключей внедряется
 * параметром, поэтому тесты подписывают фикстуры собственной P-256-парой и не ходят в интернет.
 * Решение здесь, деньги (guard, прогресс) — в `ad-reward-progress.js`.
 */

const crypto = require("node:crypto");

/** Адрес публичных ключей подписи. Публичный материал: секрета здесь нет и быть не должно. */
const ADMOB_VERIFIER_KEYS_URL = "https://gstatic.com/admob/reward/verifier-keys.json";

/** Итог проверки подписи и ключа: решение дальше делает транзакция подтверждения. */
const AD_REWARD_READY = "CONFIRMED_READY";const AD_REWARD_SIGNATURE_INVALID = "SIGNATURE_INVALID";
const AD_REWARD_KEY_UNKNOWN = "KEY_UNKNOWN";

/** Что стало с callback'ом: закрытый список, запись без вердикта не бывает. */
const AD_REWARD_CONFIRMED = "CONFIRMED";
const AD_REWARD_REPLAYED = "REPLAYED";
const AD_REWARD_FOREIGN = "FOREIGN";
const AD_REWARD_FORGED = "FORGED";
const AD_REWARD_UNUSABLE = "UNUSABLE";
const AD_REWARD_UNVERIFIABLE = "UNVERIFIABLE";
const AD_REWARD_CALLBACK_VERDICTS = Object.freeze([
  AD_REWARD_CONFIRMED,
  AD_REWARD_REPLAYED,
  AD_REWARD_FOREIGN,
  AD_REWARD_FORGED,
  AD_REWARD_UNUSABLE,
  AD_REWARD_UNVERIFIABLE,
]);

/** Причины, по которым callback не доходит до проверки подписи. */
const REASON_UNPARSEABLE = "UNPARSEABLE";
const REASON_DUPLICATE_PARAMETER = "DUPLICATE_PARAMETER";
const REASON_MISSING_TRANSACTION_ID = "MISSING_TRANSACTION_ID";
const REASON_BAD_TRANSACTION_ID = "BAD_TRANSACTION_ID";
const REASON_MISSING_USER_ID = "MISSING_USER_ID";
const REASON_BAD_USER_ID = "BAD_USER_ID";
const REASON_MISSING_KEY_ID = "MISSING_KEY_ID";
const REASON_BAD_KEY_ID = "BAD_KEY_ID";
/** Подпись честная, а аккаунта нет: начислять некому, подтверждение не расходуется зря — записано. */
const REASON_UNKNOWN_ACCOUNT = "UNKNOWN_ACCOUNT";

const TRANSACTION_ID_PATTERN = /^[A-Za-z0-9-]{8,128}$/;

function nonNegativeInt(value) {
  const number = Math.floor(Number(value));
  return Number.isFinite(number) && number > 0 ? number : 0;
}

function sha256Hex(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

/**
 * Форма uid — как везде на сервере: непустая строка без пробелов по краям и без «/».
 * `user_id` из callback'а — единственная связь подтверждения с аккаунтом.
 */
function isValidRewardUid(value) {
  return typeof value === "string" && value.length > 0 && value.length <= 128 &&
    value === value.trim() && !value.includes("/");
}

/**
 * Разбор сырой query-строки SSV-callback'а.
 *
 * Контент подписи — `rawQuery` до `&signature=` включительно по байтам: порядок параметров и
 * экранирование не трогаются, иначе подпись не сойдётся. Параметры читаются из той же строки
 * вручную: `URLSearchParams` превращал бы «+» в пробел, а в Base64-подписи «+» — законный знак.
 * Неизвестный параметр записывается и игнорируется — сеть вправе добавить свой; повтор имени
 * делает разбор невозможным: какой из двух значений подписан, решать некому.
 *
 * @returns null, если строки не существует или подписи в ней нет; иначе объект с контентом,
 *   полями и `unusableReason` — null, когда callback доходит до проверки подписи.
 */
function readAdRewardCallback(rawQuery) {
  if (typeof rawQuery !== "string" || rawQuery.length === 0 || rawQuery.length > 8192) return null;
  const separator = rawQuery.indexOf("&signature=");
  if (separator <= 0) return null;
  const content = rawQuery.slice(0, separator);
  const fields = {};
  let duplicate = false;
  for (const pair of rawQuery.split("&")) {
    const eq = pair.indexOf("=");
    if (eq <= 0) return null;
    const name = pair.slice(0, eq);
    // Битое процента-экранирование («%ZZ») — нечитаемый запрос, а не падение разбора: оно отвечает
    // записью UNUSABLE и 200, как всякий мусор, вместо 500, который Google честно повторит.
    let value;
    try {
      value = decodeURIComponent(pair.slice(eq + 1).replace(/\+/g, "%2B"));
    } catch (_) {
      return null;
    }
    if (fields[name] !== undefined) duplicate = true;
    fields[name] = value;
  }
  if (duplicate) {
    return {content, params: null, unusableReason: REASON_DUPLICATE_PARAMETER};
  }
  const params = {
    adNetwork: String(fields.ad_network || "").slice(0, 128),
    adUnit: String(fields.ad_unit || "").slice(0, 128),
    customData: String(fields.custom_data || "").slice(0, 512),
    keyId: String(fields.key_id || ""),
    rewardAmount: String(fields.reward_amount || "").slice(0, 64),
    rewardItem: String(fields.reward_item || "").slice(0, 128),
    signature: String(fields.signature || ""),
    timestamp: String(fields.timestamp || "").slice(0, 32),
    transactionId: String(fields.transaction_id || ""),
    userId: String(fields.user_id || ""),
  };
  const keyIdNumber = Number(params.keyId);
  let unusableReason = null;
  if (!TRANSACTION_ID_PATTERN.test(params.transactionId)) {
    unusableReason = params.transactionId ? REASON_BAD_TRANSACTION_ID : REASON_MISSING_TRANSACTION_ID;
  } else if (!isValidRewardUid(params.userId)) {
    unusableReason = params.userId ? REASON_BAD_USER_ID : REASON_MISSING_USER_ID;
  } else if (!params.keyId || !Number.isSafeInteger(keyIdNumber) || keyIdNumber <= 0) {
    unusableReason = params.keyId ? REASON_BAD_KEY_ID : REASON_MISSING_KEY_ID;
  }
  return {content, params, unusableReason};
}

/** Query-строка из полного URL запроса: всё после первого «?»; без него — пусто. */
function adRewardQueryFromUrl(url) {
  const text = String(url || "");
  const question = text.indexOf("?");
  return question >= 0 ? text.slice(question + 1) : "";
}

/**
 * Подпись над контентом, ключ из списка сети: true — только при честной подписи.
 *
 * Любая ошибка формы (чужой Base64, битый ключ) — «подпись не сошлась»: решений у модуля два,
 * и третьего не выдумывается.
 */
function verifyAdRewardSignature({content, signatureBase64, publicKeyPem}) {
  if (typeof publicKeyPem !== "string" || !publicKeyPem) return false;
  try {
    return crypto.verify(
      "sha256",
      Buffer.from(content, "utf8"),
      publicKeyPem,
      Buffer.from(String(signatureBase64 || ""), "base64"),
    );
  } catch (_) {
    return false;
  }
}

/**
 * Выбор публичного ключа по `key_id` из документа сети.
 *
 * Документ — `{keys: [{keyId, pem, base64}]}`; читается только `pem`, испорченная запись
 * пропускается, документа нет или ключа в нём нет — null: у wiring это сигнал обновить список.
 */
function selectVerifierKey(keysDocument, keyId) {
  const wanted = Number(keyId);
  if (!keysDocument || !Array.isArray(keysDocument.keys)) return null;
  for (const entry of keysDocument.keys) {
    if (!entry || Number(entry.keyId) !== wanted || typeof entry.pem !== "string" || !entry.pem) continue;
    return entry.pem;
  }
  return null;
}

/** Id подтверждения: транзакция сети — единственный ключ повторов, хэш — только имя документа. */
function adRewardConfirmationId(transactionId) {
  return sha256Hex(String(transactionId));
}

/** Id записи callback'а: повтор доставки того же сообщения пишет ту же строку. */
function adRewardCallbackId(callback) {
  return sha256Hex(`${callback.content}&signature=${callback.params ? callback.params.signature : ""}`);
}
/** Запись callback'а — write-once журнал: и повтор, и подделка остаются доказательством. */
function adRewardCallbackRecord({callback, verdict, reason, nowMs}) {
  const params = callback.params || {};
  return {
    schemaVersion: 1,
    id: adRewardCallbackId(callback),
    verdict,
    reason: reason || null,
    transactionId: params.transactionId || null,
    userId: params.userId || null,
    keyId: params.keyId ? Number(params.keyId) || null : null,
    adNetwork: params.adNetwork || null,
    adUnit: params.adUnit || null,
    rewardAmount: params.rewardAmount || null,
    rewardItem: params.rewardItem || null,
    rewardTimestampMs: params.timestamp ? nonNegativeInt(params.timestamp) : null,
    receivedAtMs: nonNegativeInt(nowMs),
  };
}

/** Guard подтверждения — то, что делает повтор просмотра «уже посчитанным». */
function adRewardConfirmationRecord({callback, nowMs}) {
  const params = callback.params;
  return {
    schemaVersion: 1,
    transactionId: params.transactionId,
    userId: params.userId,
    adNetwork: params.adNetwork || null,
    adUnit: params.adUnit || null,
    rewardAmount: params.rewardAmount || null,
    rewardItem: params.rewardItem || null,
    rewardTimestampMs: nonNegativeInt(params.timestamp),
    confirmedAtMs: nonNegativeInt(nowMs),
  };
}

module.exports = {
  ADMOB_VERIFIER_KEYS_URL,
  AD_REWARD_CALLBACK_VERDICTS,
  AD_REWARD_CONFIRMED,
  AD_REWARD_FORGED,
  AD_REWARD_FOREIGN,
  AD_REWARD_KEY_UNKNOWN,
  AD_REWARD_READY,
  AD_REWARD_REPLAYED,
  AD_REWARD_SIGNATURE_INVALID,
  AD_REWARD_UNUSABLE,
  AD_REWARD_UNVERIFIABLE,
  REASON_BAD_KEY_ID,
  REASON_BAD_TRANSACTION_ID,
  REASON_BAD_USER_ID,
  REASON_DUPLICATE_PARAMETER,
  REASON_MISSING_KEY_ID,
  REASON_MISSING_TRANSACTION_ID,
  REASON_MISSING_USER_ID,
  REASON_UNKNOWN_ACCOUNT,
  REASON_UNPARSEABLE,
  adRewardCallbackId,
  adRewardCallbackRecord,
  adRewardConfirmationId,
  adRewardConfirmationRecord,
  adRewardQueryFromUrl,
  isValidRewardUid,
  readAdRewardCallback,
  selectVerifierKey,
  sha256Hex,
  verifyAdRewardSignature,
};
