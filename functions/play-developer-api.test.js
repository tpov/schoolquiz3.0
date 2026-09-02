"use strict";

const assert = require("assert");
const {
  NOT_FOUND_REASONS,
  PACKAGE_NAME,
  REQUEST_TIMEOUT_MS,
  createPlayDeveloperApi,
  productPurchaseUrl,
} = require("./play-developer-api");
const {
  PLAY_OUTCOME_FOUND,
  PLAY_OUTCOME_NOT_FOUND,
  PLAY_OUTCOME_UNAVAILABLE,
} = require("./purchase-verification");

/**
 * Клиент без сети: аутентификация подменяется, и проверяется то, что от клиента требуется —
 * точный URL, исход по коду ответа и то, что наружу никогда не уходит токен.
 */
const SKU = "gold_pack_small";
/** Нарочно с символами, которые в пути надо кодировать. */
const TOKEN = "abc/def+ghi jkl?x=1&y=2#z";

function fakeAuth(handler) {
  const calls = [];
  return {
    calls,
    async getClient() {
      return {
        async request(options) {
          calls.push(options);
          return handler(options);
        },
      };
    },
  };
}

/** Ошибка в форме gaxios: сообщение несёт URL — как у настоящей. */
function gaxiosError({status = null, reason = null, code = null, url}) {
  const error = new Error(`Request failed${status ? ` with status code ${status}` : ""} for ${url}`);
  if (status !== null) {
    error.status = status;
    error.response = {
      status,
      data: reason ? {error: {code: status, message: `${reason} at ${url}`, errors: [{reason}]}} : {},
    };
  }
  if (code) error.code = code;
  return error;
}

function api(handler, options = {}) {
  const auth = fakeAuth(handler);
  return {auth, client: createPlayDeveloperApi({auth, ...options})};
}

function assertNeverCarriesTheToken(result, label) {
  assert.strictEqual(JSON.stringify(result).includes(TOKEN), false, `${label}: токен наружу`);
  assert.strictEqual(JSON.stringify(result).includes(encodeURIComponent(TOKEN)), false, `${label}: токен в URL наружу`);
  assert.strictEqual("message" in result, false, `${label}: текста ошибки нет`);
}

async function testTheRequestNamesThePackageSkuAndTokenExactly() {
  const {auth, client} = api(() => ({status: 200, data: {productId: SKU}}));

  await client.getProductPurchase(SKU, TOKEN);

  assert.strictEqual(auth.calls.length, 1);
  const {url, method, timeout} = auth.calls[0];
  assert.strictEqual(
    url,
    "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.tpov.schoolquiz" +
      `/purchases/products/gold_pack_small/tokens/${encodeURIComponent(TOKEN)}`,
  );
  assert.strictEqual(url, productPurchaseUrl(PACKAGE_NAME, SKU, TOKEN));
  assert.ok(!url.includes(" ") && !url.includes("#") && !url.includes("?"), "путь закодирован");
  assert.strictEqual(method, "GET");
  assert.strictEqual(timeout, REQUEST_TIMEOUT_MS);
  assert.strictEqual(REQUEST_TIMEOUT_MS, 10000);

  // Другой пакет — другой путь; SKU тоже кодируется.
  const other = api(() => ({status: 200, data: {}}), {packageName: "com.example.app"});
  await other.client.getProductPurchase("sku.with/slash", "t");
  assert.ok(other.auth.calls[0].url.includes("/applications/com.example.app/purchases/products/sku.with%2Fslash/tokens/t"));
}

async function testAFoundPurchaseIsReturnedAsPlaySentIt() {
  const data = {productId: SKU, purchaseState: 0, orderId: "GPA.1", purchaseTimeMillis: "1"};
  const {client} = api(() => ({status: 200, data}));

  const result = await client.getProductPurchase(SKU, TOKEN);

  assert.deepStrictEqual(result, {outcome: PLAY_OUTCOME_FOUND, status: 200, code: null, reason: null, purchase: data});
}

async function testA404OrA400AboutTheTokenMeansPlayDoesNotKnowIt() {
  const missing = api(({url}) => {
    throw gaxiosError({status: 404, reason: "notFound", url});
  });
  const notFound = await missing.client.getProductPurchase(SKU, TOKEN);
  assert.strictEqual(notFound.outcome, PLAY_OUTCOME_NOT_FOUND);
  assert.strictEqual(notFound.status, 404);
  assertNeverCarriesTheToken(notFound, "404");

  for (const reason of NOT_FOUND_REASONS) {
    const {client} = api(({url}) => {
      throw gaxiosError({status: 400, reason, url});
    });
    const result = await client.getProductPurchase(SKU, TOKEN);
    assert.deepStrictEqual(result, {outcome: PLAY_OUTCOME_NOT_FOUND, status: 400, code: null, reason}, reason);
    assertNeverCarriesTheToken(result, reason);
  }
  assert.ok(NOT_FOUND_REASONS.includes("invalidPurchaseToken"));
}

async function testA400AboutAnythingElseIsNotAVerdict() {
  // 400 у этого API — и «токен поддельный», и «запрос не той формы». Второе — наша ошибка, и
  // повторить позже правильнее, чем назвать игрока мошенником.
  for (const reason of ["badRequest", "invalid", null]) {
    const {client} = api(({url}) => {
      throw gaxiosError({status: 400, reason, url});
    });
    const result = await client.getProductPurchase(SKU, TOKEN);
    assert.strictEqual(result.outcome, PLAY_OUTCOME_UNAVAILABLE, String(reason));
    assert.strictEqual(result.status, 400);
    assert.strictEqual(result.reason, reason);
    assertNeverCarriesTheToken(result, String(reason));
  }
}

async function testAccessDeniedAndServerErrorsAreUnavailable() {
  for (const status of [401, 403, 500, 503]) {
    const {client} = api(({url}) => {
      throw gaxiosError({status, reason: status < 500 ? "forbidden" : null, url});
    });
    const result = await client.getProductPurchase(SKU, TOKEN);
    assert.strictEqual(result.outcome, PLAY_OUTCOME_UNAVAILABLE, String(status));
    assert.strictEqual(result.status, status);
    assert.strictEqual(result.code, null);
    assertNeverCarriesTheToken(result, String(status));
  }
}

async function testANetworkErrorWithoutAStatusIsUnavailableAndKeepsItsCode() {
  const {client} = api(({url}) => {
    throw gaxiosError({code: "ECONNRESET", url});
  });

  const result = await client.getProductPurchase(SKU, TOKEN);

  assert.deepStrictEqual(result, {outcome: PLAY_OUTCOME_UNAVAILABLE, status: null, code: "ECONNRESET", reason: null});
  assertNeverCarriesTheToken(result, "ECONNRESET");

  // Аутентификация не дала клиента — тоже недоступность, не вердикт.
  const noAuth = createPlayDeveloperApi({
    auth: {
      async getClient() {
        throw new Error(`could not load the default credentials for ${TOKEN}`);
      },
    },
  });
  const denied = await noAuth.getProductPurchase(SKU, TOKEN);
  assert.strictEqual(denied.outcome, PLAY_OUTCOME_UNAVAILABLE);
  assertNeverCarriesTheToken(denied, "no credentials");
}

(async () => {
  await testTheRequestNamesThePackageSkuAndTokenExactly();
  await testAFoundPurchaseIsReturnedAsPlaySentIt();
  await testA404OrA400AboutTheTokenMeansPlayDoesNotKnowIt();
  await testA400AboutAnythingElseIsNotAVerdict();
  await testAccessDeniedAndServerErrorsAreUnavailable();
  await testANetworkErrorWithoutAStatusIsUnavailableAndKeepsItsCode();
  console.log("play-developer-api.test.js OK");
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
