"use strict";

/**
 * Тонкий клиент Play Developer API: один запрос — состояние покупки по токену.
 *
 * Единственный источник факта о покупке. Чек, который принёс клиент, — утверждение; ответ Play на
 * `purchases.products.get` — то, по чему сервер решает начислять (purchase-verification.js). Здесь
 * нет решений: только запрос и сопоставление ответа с исходом.
 *
 * Учётные данные — только Application Default Credentials: сервисный аккаунт функции, которому
 * владелец даёт доступ в Play Console (Users and permissions: View financial data, Manage orders).
 * Ключа в переменных окружения нет намеренно: ключ в `.env` хуже неудобства с порядком деплоя,
 * которое он обходил бы. Когда владелец заведёт секрет, сюда придёт `defineSecret`.
 *
 * Исходы — словарь purchase-verification.js (`PLAY_OUTCOME_*`): что каждый из них значит для
 * решения, проверено там. Наружу уходят исход, код HTTP, код ошибки и причина из тела ответа
 * Play — и никогда текст ошибки: gaxios вкладывает в сообщение URL, а URL несёт сырой токен.
 *
 * `google-auth-library` уже стоит транзитивно через firebase-admin; здесь она объявлена явно.
 * Аутентификация инжектируется, поэтому клиент проверяется без сети (play-developer-api.test.js).
 */

const {GoogleAuth} = require("google-auth-library");
const {
  PLAY_OUTCOME_FOUND,
  PLAY_OUTCOME_NOT_FOUND,
  PLAY_OUTCOME_UNAVAILABLE,
} = require("./purchase-verification");

/** Пакет приложения. Токен другого пакета Play не найдёт — и это правильный ответ. */
const PACKAGE_NAME = "com.tpov.schoolquiz";
const ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const API_ROOT = "https://androidpublisher.googleapis.com/androidpublisher/v3";
/** Игрок ждёт на экране; дольше этого ответ Play уже не ответ, а повод повторить позже. */
const REQUEST_TIMEOUT_MS = 10000;

/**
 * Причины ответа 400, означающие «Play токена не знает».
 *
 * 400 у этого API — и «токен поддельный», и «запрос не той формы». Первое — вердикт о токене и
 * окончательный отказ; второе — наша ошибка, и повторить позже правильнее, чем назвать игрока
 * мошенником. Различаются по `error.errors[0].reason` в теле; нет причины — считаем вторым.
 */
const NOT_FOUND_REASONS = Object.freeze([
  "invalidPurchaseToken",
  "purchaseTokenDoesNotMatchProductId",
  "productNotOwnedByUser",
  "purchaseTokenNoLongerValid",
]);

/**
 * Клиент с инжектируемой аутентификацией.
 *
 * @param {object} [options]
 * @param {string} [options.packageName] пакет вместо {@link PACKAGE_NAME}.
 * @param {object} [options.auth] готовый `GoogleAuth` — для подмены в тестах.
 */
function createPlayDeveloperApi(options = {}) {
  const packageName = options.packageName || PACKAGE_NAME;
  const auth = options.auth || new GoogleAuth({scopes: [ANDROID_PUBLISHER_SCOPE]});

  return {
    packageName,

    /**
     * Состояние покупки одноразового товара.
     *
     * @param {string} sku SKU, который назвал клиент; Play сверит его с токеном сам.
     * @param {string} purchaseToken токен покупки от Play Billing.
     * @returns {Promise<{outcome: string, status: number|null, code: string|null, reason: string|null, purchase?: object}>}
     */
    async getProductPurchase(sku, purchaseToken) {
      const url = productPurchaseUrl(packageName, sku, purchaseToken);
      try {
        const client = await auth.getClient();
        const response = await client.request({url, method: "GET", timeout: REQUEST_TIMEOUT_MS});
        return {
          outcome: PLAY_OUTCOME_FOUND,
          status: response.status,
          code: null,
          reason: null,
          purchase: response.data,
        };
      } catch (error) {
        return classifyFailure(error);
      }
    },
  };
}

function productPurchaseUrl(packageName, sku, purchaseToken) {
  return `${API_ROOT}/applications/${encodeURIComponent(packageName)}` +
    `/purchases/products/${encodeURIComponent(sku)}/tokens/${encodeURIComponent(purchaseToken)}`;
}

/**
 * Исход по ошибке запроса. 404 и 400 с причиной о токене — не знает; всё остальное — не ответ о
 * токене, а повод повторить. 401/403 — тоже повтор, но обвязка пишет их ошибкой: это не токен
 * виноват, а доступ сервисного аккаунта.
 */
function classifyFailure(error) {
  const status = httpStatus(error);
  const reason = playErrorReason(error);
  const notFound = status === 404 || (status === 400 && NOT_FOUND_REASONS.includes(reason));
  return {
    outcome: notFound ? PLAY_OUTCOME_NOT_FOUND : PLAY_OUTCOME_UNAVAILABLE,
    status,
    code: error && error.code ? error.code : null,
    reason,
  };
}

/** Код HTTP из ошибки gaxios; у сетевой ошибки его нет. */
function httpStatus(error) {
  if (!error) return null;
  if (Number.isInteger(error.status)) return error.status;
  if (error.response && Number.isInteger(error.response.status)) return error.response.status;
  const code = Number(error.code);
  return Number.isInteger(code) && code >= 100 && code <= 599 ? code : null;
}

/** `error.errors[0].reason` из тела ответа Play, если оно есть. */
function playErrorReason(error) {
  const body = error && error.response && error.response.data;
  const errors = body && body.error && Array.isArray(body.error.errors) ? body.error.errors : [];
  const first = errors[0];
  return first && typeof first.reason === "string" && first.reason ? first.reason : null;
}

module.exports = {
  ANDROID_PUBLISHER_SCOPE,
  NOT_FOUND_REASONS,
  PACKAGE_NAME,
  REQUEST_TIMEOUT_MS,
  createPlayDeveloperApi,
  productPurchaseUrl,
};
