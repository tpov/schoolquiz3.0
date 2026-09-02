"use strict";

const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const {
  AUDIT_ACTION_PURCHASE_CREDIT,
  PLAY_OUTCOME_FOUND,
  PLAY_OUTCOME_NOT_FOUND,
  PLAY_OUTCOME_UNAVAILABLE,
  REASON_CODES,
  REASON_CONSUMED_WITHOUT_SETTLEMENT,
  REASON_CONSUMPTION_STATE_UNKNOWN,
  REASON_PURCHASE_CANCELED,
  REASON_PURCHASE_STATE_UNKNOWN,
  REASON_QUANTITY_UNREADABLE,
  REASON_SETTLEMENT_NOT_CREDITED,
  REASON_SKU_MISMATCH,
  REASON_SKU_NOT_SOLD,
  REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT,
  REASON_TOKEN_UNKNOWN,
  RECEIPT_KIND_PURCHASE_CREDITED,
  SETTLEMENT_CREDIT,
  SETTLEMENT_PENDING,
  SETTLEMENT_REFUSE,
  SETTLEMENT_REPLAY,
  STATUS_CREDITED,
  STATUS_PENDING,
  accountIdFor,
  auditRecord,
  decideSettlement,
  purchaseFromLookup,
  readProductPurchase,
  receiptRecord,
  settlementGate,
  settlementId,
  settlementRecord,
  successResponse,
  validatePurchaseRequest,
} = require("./purchase-verification");
const {ECONOMY_CONSTANTS_DOC, readEconomyConstants} = require("./economy-constants");

const NOW = 1_800_000_000_000;
const UID = "player-1";
const TOKEN = "hlmbchfnkkjlbhdejkjhkkbo.AO-J1OyExampleTokenValue-1234567890";
const SKU_SMALL = "gold_pack_small";
const SKU_MEDIUM = "gold_pack_medium";
/** Документа `configs/economy` нет — начальные значения, версия 0. */
const BOOTSTRAP = readEconomyConstants(null);

/** Ответ Play на `purchases.products.get`, каким он приезжает: int64 строками, поля по желанию. */
function playPurchase(overrides = {}) {
  return readProductPurchase({
    kind: "androidpublisher#productPurchase",
    purchaseTimeMillis: "1799999000000",
    purchaseState: 0,
    consumptionState: 0,
    orderId: "GPA.3333-4444-5555-66666",
    acknowledgementState: 0,
    productId: SKU_SMALL,
    quantity: 1,
    regionCode: "UA",
    ...overrides,
  });
}

function decide(overrides = {}) {
  return decideSettlement({
    existing: null,
    purchase: playPurchase(),
    claimedSku: SKU_SMALL,
    uid: UID,
    constants: BOOTSTRAP,
    ...overrides,
  });
}

/** Что записала бы транзакция по решению «начислить» — тем же порядком, что в purchase-settlement.js. */
function settle(verdict, {goldBefore = 5, purchase = playPurchase(), uid = UID} = {}) {
  assert.strictEqual(verdict.decision, SETTLEMENT_CREDIT, verdict.reason);
  const id = settlementId(TOKEN);
  const goldAfter = goldBefore + verdict.credit.goldGranted;
  const response = successResponse({
    sku: verdict.credit.sku,
    goldGranted: verdict.credit.goldGranted,
    gold: goldAfter,
    settlementId: id,
  });
  const bag = {
    id, uid, purchase, credit: verdict.credit, goldBefore, goldAfter,
    constantsDoc: ECONOMY_CONSTANTS_DOC, now: NOW,
  };
  return {
    response,
    settlement: settlementRecord({...bag, response}),
    receipt: receiptRecord(bag),
    audit: auditRecord(bag),
  };
}

function assertRefused(verdict, code, reasonCode, label = "") {
  assert.strictEqual(verdict.decision, SETTLEMENT_REFUSE, `${label} decision`);
  assert.strictEqual(verdict.code, code, `${label} code`);
  assert.strictEqual(verdict.reasonCode, reasonCode, `${label} reasonCode`);
  assert.strictEqual(verdict.response, undefined, `${label} отказ не несёт ответа`);
  assert.strictEqual(verdict.credit, undefined, `${label} отказ ничего не начисляет`);
}

function testAVerifiedUnseenTokenIsCreditedFromTheServerTable() {
  // Сумму называет таблица, не запрос: клиент, называющий цену, назовёт любую.
  const verdict = decide();

  assert.strictEqual(verdict.decision, SETTLEMENT_CREDIT);
  assert.deepStrictEqual(verdict.credit, {
    sku: SKU_SMALL,
    goldPerUnit: 10,
    quantity: 1,
    goldGranted: 10,
    isTest: false,
    constantsVersion: 0,
  });

  const {response} = settle(verdict, {goldBefore: 5});
  assert.deepStrictEqual(response, {
    status: STATUS_CREDITED,
    sku: SKU_SMALL,
    goldGranted: 10,
    gold: 15,
    settlementId: settlementId(TOKEN),
  });

  // Количество берётся из ответа Play, а не из запроса.
  const three = decide({purchase: playPurchase({quantity: 3})});
  assert.strictEqual(three.credit.goldGranted, 30);
  assert.strictEqual(three.credit.quantity, 3);
}

function testTheSameTokenAgainReplaysTheStoredAnswerAndMovesNothing() {
  // Ради этого случая ключ и существует: Play доставляет непогашенную покупку при каждом
  // подключении, а сеть может оборвать ответ уже после начисления.
  const {settlement, response} = settle(decide());

  // После первого ответа клиент погасил покупку — Play теперь отвечает «погашена». Или Play
  // недоступен вовсе. Повтор всё равно получает то же, сколько бы раз его ни спросили.
  const laterAnswers = [playPurchase({consumptionState: 1}), null, playPurchase()];
  for (const purchase of laterAnswers) {
    const again = decide({existing: settlement, purchase});
    assert.strictEqual(again.decision, SETTLEMENT_REPLAY);
    assert.deepStrictEqual(again.response, response);
    assert.strictEqual(again.credit, undefined, "повтор ничего не начисляет");
  }

  // Та же проверка, которой вызов отвечает до того, как спросить Play.
  const gate = settlementGate(settlement, UID);
  assert.strictEqual(gate.decision, SETTLEMENT_REPLAY);
  assert.deepStrictEqual(gate.response, response);
  assert.strictEqual(settlementGate(null, UID), null, "без записи решает полный разбор");
}

function testAReplayOfARecordWithoutAStoredResponseRebuildsItFromTheRecord() {
  // Запись, правленная руками, без сохранённого ответа: игрок всё равно должен получить ответ,
  // по которому сможет погасить покупку, — и ровно тот, что был бы сохранён.
  const {settlement} = settle(decide(), {goldBefore: 7});
  const bare = {...settlement, response: null};

  const gate = settlementGate(bare, UID);

  assert.strictEqual(gate.decision, SETTLEMENT_REPLAY);
  assert.deepStrictEqual(gate.response, successResponse({
    sku: bare.sku,
    goldGranted: bare.goldGranted,
    gold: bare.goldAfter,
    settlementId: bare.settlementId,
  }));
  assert.strictEqual(gate.response.gold, 17, "баланс — снимок на момент начисления, не текущий");
}

function testASettlementThatIsNotCreditedIsRefusedRatherThanReplayed() {
  // Возврат (история 1.3) переведёт запись из «начислено» в своё состояние. Повторять по такой
  // записи ответ «начислено» значило бы дать погасить то, за что деньги уже вернули.
  const {settlement} = settle(decide());
  const refunded = {...settlement, status: "REFUNDED"};

  assertRefused(settlementGate(refunded, UID), "failed-precondition", REASON_SETTLEMENT_NOT_CREDITED, "gate");
  assertRefused(decide({existing: refunded}), "failed-precondition", REASON_SETTLEMENT_NOT_CREDITED, "decide");
  // Владелец проверяется раньше состояния: чужая возвращённая запись — всё ещё чужая.
  assertRefused(
    settlementGate(refunded, "someone-else"), "permission-denied", REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT, "foreign",
  );
}

function testAPendingPurchaseGrantsNothingAndIsNotAnError() {
  // Оплата дорешается позже тем же путём. Ни начисления, ни отказа — иначе клиент либо погасит
  // неоплаченное, либо покажет ошибку тому, кто ещё платит.
  const verdict = decide({purchase: playPurchase({purchaseState: 2})});

  assert.strictEqual(verdict.decision, SETTLEMENT_PENDING);
  assert.deepStrictEqual(verdict.response, {status: STATUS_PENDING, sku: SKU_SMALL});
  assert.strictEqual(verdict.code, undefined, "ожидание — не ошибка");
  assert.strictEqual(verdict.reasonCode, undefined);
  assert.strictEqual(verdict.credit, undefined);
}

function testACanceledPurchaseIsRefused() {
  assertRefused(
    decide({purchase: playPurchase({purchaseState: 1})}), "failed-precondition", REASON_PURCHASE_CANCELED,
  );
}

function testATokenPlayDoesNotKnowIsRefused() {
  // Подделка или чужой пакет: Play ответил 404 (или 400 о токене), и клиент прочитал это как
  // «покупки нет».
  assertRefused(decide({purchase: null}), "permission-denied", REASON_TOKEN_UNKNOWN);
}

function testAnUnreachablePlayIsNotAVerdictAndTheClientRetries() {
  // Сеть, 5xx, отказ в доступе: Play ничего не сказал о токене. Это не вердикт — ничего не
  // пишется, и вызов отвечает unavailable, чтобы клиент повторил позже тем же путём; покупка до
  // тех пор остаётся непогашенной. Незнакомый исход — тоже «повторить», а не «отказать».
  const notVerdicts = [
    {outcome: PLAY_OUTCOME_UNAVAILABLE, status: 503, code: null, reason: null},
    {outcome: PLAY_OUTCOME_UNAVAILABLE, status: null, code: "ECONNRESET", reason: null},
    {outcome: PLAY_OUTCOME_UNAVAILABLE, status: 403, code: null, reason: "forbidden"},
    {outcome: PLAY_OUTCOME_UNAVAILABLE, status: 400, code: null, reason: "badRequest"},
    {outcome: "something-new"},
    null,
    // Найдено, но тело не читается: Play ответил, значит, токен настоящий, а форма — наша беда.
    {outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: "text"},
    {outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: null},
    {outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: [1, 2]},
  ];
  for (const lookup of notVerdicts) {
    const read = purchaseFromLookup(lookup);
    assert.strictEqual(read.unavailable, true, JSON.stringify(lookup));
    assert.strictEqual(read.purchase, null);
  }

  // 404, или 400 с причиной о токене — Play токена не знает. Это вердикт, и он отрицательный.
  const missing = purchaseFromLookup({outcome: PLAY_OUTCOME_NOT_FOUND, status: 404, code: null, reason: null});
  assert.deepStrictEqual(missing, {unavailable: false, purchase: null});
  assertRefused(decide({purchase: missing.purchase}), "permission-denied", REASON_TOKEN_UNKNOWN);

  const found = purchaseFromLookup({
    outcome: PLAY_OUTCOME_FOUND, status: 200, purchase: {purchaseState: 0, productId: SKU_SMALL},
  });
  assert.strictEqual(found.unavailable, false);
  assert.strictEqual(found.purchase.productId, SKU_SMALL);
  assert.strictEqual(decide({purchase: found.purchase}).decision, SETTLEMENT_CREDIT);
}

function testAClaimedSkuThatDiffersFromPlaysIsRefused() {
  // Клиент купил маленький пак, а назвал средний. Сумма считается по названному — поэтому
  // названное обязано совпасть с тем, за что Play получил деньги.
  assertRefused(
    decide({claimedSku: SKU_MEDIUM, purchase: playPurchase({productId: SKU_SMALL})}),
    "invalid-argument", REASON_SKU_MISMATCH,
  );
}

function testAnUnsoldSkuIsRefusedRatherThanCreditedAtZero() {
  // Премиум и коробки — эпики 2 и 3; их SKU в `goldPacks` нет. Неизвестный SKU — тоже.
  for (const sku of ["premium_month", "box_single", "gold_pack_gigantic"]) {
    assertRefused(
      decide({claimedSku: sku, purchase: playPurchase({productId: sku})}), "invalid-argument", REASON_SKU_NOT_SOLD, sku,
    );
  }

  // Таблица говорит «ноль» — тоже не продаётся: клиенту нельзя ответить «начислено» за ничего.
  // Читалка таблицы ноль не пропускает; здесь проверяется сам решатель, если ноль всё же дошёл.
  const zeroTable = {...BOOTSTRAP, goldPacks: {[SKU_SMALL]: 0}};
  assertRefused(decide({constants: zeroTable}), "invalid-argument", REASON_SKU_NOT_SOLD, "zero");
  const oddTable = {...BOOTSTRAP, goldPacks: {[SKU_SMALL]: "10"}};
  assertRefused(decide({constants: oddTable}), "invalid-argument", REASON_SKU_NOT_SOLD, "string");
}

function testATokenSettledByAnotherAccountIsRefusedWithoutLeakingItsAnswer() {
  // Чужой токен — не повтор, а попытка прочитать чужой результат: ни начисления, ни ответа.
  const {settlement} = settle(decide(), {uid: "someone-else"});

  assertRefused(decide({existing: settlement}), "permission-denied", REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT, "decide");
  assertRefused(settlementGate(settlement, UID), "permission-denied", REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT, "gate");
}

function testAPurchaseMarkedForAnotherAccountIsRefused() {
  // Клиент (1.2) помечает покупку хешем uid; Play возвращает метку как есть. Метка есть и не
  // совпадает с предъявителем — платил другой. Метки нет — покупка сделана до того, как клиент
  // начал её ставить, и проходит по одному только токену.
  assert.strictEqual(accountIdFor(UID), crypto.createHash("sha256").update(UID).digest("hex"));
  assert.match(accountIdFor(UID), /^[0-9a-f]{64}$/);

  const mine = decide({purchase: playPurchase({obfuscatedExternalAccountId: accountIdFor(UID)})});
  assert.strictEqual(mine.decision, SETTLEMENT_CREDIT, "своя метка — начисляется");

  const unmarked = decide({purchase: playPurchase({obfuscatedExternalAccountId: undefined})});
  assert.strictEqual(unmarked.decision, SETTLEMENT_CREDIT, "без метки — по токену");
  assert.strictEqual(unmarked.credit.goldGranted, 10);

  assertRefused(
    decide({purchase: playPurchase({obfuscatedExternalAccountId: accountIdFor("someone-else")})}),
    "permission-denied", REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT, "foreign",
  );
  // Метка сверяется с предъявителем, не с содержимым: «сырой» uid вместо хеша — тоже чужая.
  assertRefused(
    decide({purchase: playPurchase({obfuscatedExternalAccountId: UID})}),
    "permission-denied", REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT, "raw uid",
  );
}

function testAConsumedTokenWithoutASettlementIsNotOursToCredit() {
  // Гасит клиент после подтверждения сервера. Погашено, а подтверждения не было — значит,
  // погасили мимо этого пути, и по такому чеку начислять нечего.
  assertRefused(
    decide({purchase: playPurchase({consumptionState: 1})}),
    "failed-precondition", REASON_CONSUMED_WITHOUT_SETTLEMENT,
  );
}

function testAMissingConstantsDocumentCreditsBootstrapDefaultsAndAuditsVersionZero() {
  const {audit, settlement} = settle(decide({constants: readEconomyConstants(null)}));

  assert.strictEqual(audit.goldGranted, 10, "начальный размер маленького пака");
  assert.strictEqual(audit.constantsVersion, 0);
  assert.strictEqual(audit.constantsDoc, "configs/economy");
  assert.strictEqual(settlement.constantsVersion, 0);
}

function testATestPurchaseIsCreditedAndFlaggedInTheAudit() {
  // Лицензионный тестер платит понарошку, но начисление настоящее — иначе покупку не проверить
  // до релиза. Аудит помечает, чтобы выручка не считалась по тестовым чекам.
  const test = settle(decide({purchase: playPurchase({purchaseType: 0})}));
  assert.strictEqual(test.audit.isTest, true);
  assert.strictEqual(test.settlement.isTest, true);
  assert.strictEqual(test.audit.goldGranted, 10);

  // У обычной покупки поля `purchaseType` нет.
  const real = settle(decide({purchase: playPurchase()}));
  assert.strictEqual(real.audit.isTest, false);
  // Промо-покупка (1) — тоже не тест.
  const promo = settle(decide({purchase: playPurchase({purchaseType: 1})}));
  assert.strictEqual(promo.audit.isTest, false);
}

function testAChangedTableAppliesToTheNextPurchaseAndEarlierAuditsKeepTheirVersion() {
  // Смена цены не ретроактивна, и аудит — единственное, чем это потом доказывается.
  const before = readEconomyConstants({version: 4, goldPacks: {gold_pack_small: 10}});
  const after = readEconomyConstants({version: 5, goldPacks: {gold_pack_small: 15}});

  const first = settle(decide({constants: before}));
  const second = settle(decide({constants: after}));

  assert.strictEqual(first.audit.goldGranted, 10);
  assert.strictEqual(first.audit.constantsVersion, 4);
  assert.strictEqual(second.audit.goldGranted, 15);
  assert.strictEqual(second.audit.constantsVersion, 5);
  assert.strictEqual(second.response.goldGranted, 15);
}

function testEveryRefusalCarriesAMachineReadableReasonCode() {
  // Клиент ветвится по коду HTTPS; человек и аналитика — по коду причины, не по тексту (AD-15).
  const {settlement} = settle(decide());
  const refusals = [
    ["forged", () => decide({purchase: null}), REASON_TOKEN_UNKNOWN],
    ["sku mismatch", () => decide({claimedSku: SKU_MEDIUM}), REASON_SKU_MISMATCH],
    ["canceled", () => decide({purchase: playPurchase({purchaseState: 1})}), REASON_PURCHASE_CANCELED],
    ["odd state", () => decide({purchase: playPurchase({purchaseState: 7})}), REASON_PURCHASE_STATE_UNKNOWN],
    ["consumed", () => decide({purchase: playPurchase({consumptionState: 1})}), REASON_CONSUMED_WITHOUT_SETTLEMENT],
    ["odd consumption", () => decide({purchase: playPurchase({consumptionState: 5})}), REASON_CONSUMPTION_STATE_UNKNOWN],
    ["odd quantity", () => decide({purchase: playPurchase({quantity: 0})}), REASON_QUANTITY_UNREADABLE],
    ["unsold", () => decide({claimedSku: "box_single", purchase: playPurchase({productId: "box_single"})}), REASON_SKU_NOT_SOLD],
    ["foreign record", () => decide({existing: {...settlement, uid: "x"}}), REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT],
    ["foreign buyer", () => decide({purchase: playPurchase({obfuscatedExternalAccountId: "z"})}), REASON_TOKEN_OWNED_BY_ANOTHER_ACCOUNT],
    ["refunded record", () => decide({existing: {...settlement, status: "REFUNDED"}}), REASON_SETTLEMENT_NOT_CREDITED],
  ];
  for (const [label, run, expected] of refusals) {
    const verdict = run();
    assert.strictEqual(verdict.decision, SETTLEMENT_REFUSE, label);
    assert.strictEqual(verdict.reasonCode, expected, label);
    assert.ok(REASON_CODES.includes(verdict.reasonCode), `${label}: код из словаря`);
    assert.ok(typeof verdict.reason === "string" && verdict.reason, `${label}: текст для логов`);
  }
  assert.strictEqual(new Set(REASON_CODES).size, REASON_CODES.length, "коды не повторяются");
}

function testTheSettlementIdIsTheSha256OfTheTokenAndTheRawTokenIsStoredNowhere() {
  const id = settlementId(TOKEN);

  assert.strictEqual(id, crypto.createHash("sha256").update(TOKEN).digest("hex"));
  assert.match(id, /^[0-9a-f]{64}$/);
  assert.strictEqual(settlementId(TOKEN), id, "тот же токен — тот же документ");
  assert.notStrictEqual(settlementId(TOKEN + "x"), id);

  // Возврат (1.3) получит токен от Play и хеширует его так же; хранить сам токен незачем.
  const written = settle(decide());
  assert.strictEqual(JSON.stringify(written).includes(TOKEN), false, "сырой токен в записях");
}

function testTheRecordsNameWhatTheAuditNeeds() {
  const {settlement, receipt, audit, response} = settle(decide(), {goldBefore: 7});
  const id = settlementId(TOKEN);

  assert.deepStrictEqual(settlement, {
    settlementId: id,
    uid: UID,
    status: STATUS_CREDITED,
    sku: SKU_SMALL,
    orderId: "GPA.3333-4444-5555-66666",
    purchaseTimeMs: 1799999000000,
    quantity: 1,
    goldPerUnit: 10,
    goldGranted: 10,
    goldBefore: 7,
    goldAfter: 17,
    constantsDoc: "configs/economy",
    constantsVersion: 0,
    isTest: false,
    settledAtMs: NOW,
    response,
  });

  // Квитанция — ключ и параметры, не текст: текст собирает устройство на своём языке.
  assert.deepStrictEqual(receipt, {
    receiptId: id,
    uid: UID,
    kind: RECEIPT_KIND_PURCHASE_CREDITED,
    messageKey: "receipt.gold_pack_credited",
    params: {sku: SKU_SMALL, gold: 10, quantity: 1},
    amount: 10,
    currency: "GOLD",
    settlementId: id,
    createdAtMs: NOW,
  });

  // Аудит: кто, токен (его id), SKU, сумма, документ констант с версией.
  assert.deepStrictEqual(audit, {
    auditId: id,
    action: AUDIT_ACTION_PURCHASE_CREDIT,
    uid: UID,
    settlementId: id,
    sku: SKU_SMALL,
    orderId: "GPA.3333-4444-5555-66666",
    quantity: 1,
    goldPerUnit: 10,
    goldGranted: 10,
    goldBefore: 7,
    goldAfter: 17,
    constantsDoc: "configs/economy",
    constantsVersion: 0,
    isTest: false,
    atMs: NOW,
  });
}

function testThePlayAnswerIsNormalisedBeforeItIsJudged() {
  // Play отдаёт int64 строками и опускает необязательные поля.
  const purchase = readProductPurchase({
    purchaseTimeMillis: "1700000000000",
    purchaseState: "0",
    productId: SKU_SMALL,
  });
  assert.strictEqual(purchase.purchaseTimeMs, 1700000000000);
  assert.strictEqual(purchase.purchaseState, 0);
  assert.strictEqual(purchase.consumptionState, 0, "нет поля — не погашена");
  assert.strictEqual(purchase.quantity, 1, "нет поля — одна штука");
  assert.strictEqual(purchase.purchaseType, null);
  assert.strictEqual(purchase.isTest, false);
  assert.strictEqual(purchase.obfuscatedExternalAccountId, "", "нет метки — пустая строка");
  assert.strictEqual(decide({purchase}).decision, SETTLEMENT_CREDIT);

  // Есть поле, но не читается, — не «одна штука» и не «не погашена»: решение отказывает, а не
  // угадывает в пользу начисления.
  const oddQuantity = readProductPurchase({purchaseState: 0, quantity: 0, productId: SKU_SMALL});
  assert.strictEqual(oddQuantity.quantity, null);
  assertRefused(decide({purchase: oddQuantity}), "failed-precondition", REASON_QUANTITY_UNREADABLE, "qty 0");
  for (const quantity of [-1, 1.5, "many", true]) {
    const odd = readProductPurchase({purchaseState: 0, quantity, productId: SKU_SMALL});
    assertRefused(decide({purchase: odd}), "failed-precondition", REASON_QUANTITY_UNREADABLE, `qty ${quantity}`);
  }
  assert.strictEqual(readProductPurchase({quantity: "2"}).quantity, 2, "строка с целым читается");

  const oddConsumption = readProductPurchase({purchaseState: 0, consumptionState: "bought", productId: SKU_SMALL});
  assert.strictEqual(oddConsumption.consumptionState, null);
  assertRefused(
    decide({purchase: oddConsumption}), "failed-precondition", REASON_CONSUMPTION_STATE_UNKNOWN, "consumption",
  );
  assertRefused(
    decide({purchase: playPurchase({consumptionState: 2})}), "failed-precondition", REASON_CONSUMPTION_STATE_UNKNOWN, "2",
  );

  // Испорченное состояние покупки — не «куплено» и не «ждёт».
  const oddState = readProductPurchase({purchaseState: "bought", productId: SKU_SMALL});
  assert.strictEqual(oddState.purchaseState, null);
  assertRefused(decide({purchase: oddState}), "failed-precondition", REASON_PURCHASE_STATE_UNKNOWN, "state");

  assert.strictEqual(readProductPurchase(null), null);
  assert.strictEqual(readProductPurchase("text"), null);
  assert.strictEqual(readProductPurchase([1]), null);
}

function testARequestWithoutATokenOrWithAnOddSkuIsRefusedBeforePlayIsAsked() {
  assert.strictEqual(validatePurchaseRequest({sku: SKU_SMALL}).valid, false);
  assert.strictEqual(validatePurchaseRequest({purchaseToken: TOKEN}).valid, false);
  assert.strictEqual(validatePurchaseRequest(null).valid, false);
  assert.strictEqual(validatePurchaseRequest({purchaseToken: TOKEN, sku: "Gold Pack"}).valid, false);
  assert.strictEqual(validatePurchaseRequest({purchaseToken: TOKEN, sku: "../x"}).valid, false);
  assert.strictEqual(validatePurchaseRequest({purchaseToken: "a".repeat(2049), sku: SKU_SMALL}).valid, false);

  // Строки, а не что-то приводимое к строке: объект вместо токена дал бы всем таким запросам один
  // ключ расчёта (`String({})`), число вместо SKU — не SKU.
  assert.strictEqual(validatePurchaseRequest({purchaseToken: {token: TOKEN}, sku: SKU_SMALL}).valid, false);
  assert.strictEqual(validatePurchaseRequest({purchaseToken: [TOKEN], sku: SKU_SMALL}).valid, false);
  assert.strictEqual(validatePurchaseRequest({purchaseToken: 12345, sku: SKU_SMALL}).valid, false);
  assert.strictEqual(validatePurchaseRequest({purchaseToken: TOKEN, sku: 7}).valid, false);
  assert.strictEqual(validatePurchaseRequest({purchaseToken: TOKEN, sku: {sku: SKU_SMALL}}).valid, false);

  const ok = validatePurchaseRequest({purchaseToken: TOKEN, sku: SKU_SMALL});
  assert.deepStrictEqual(ok, {valid: true, purchaseToken: TOKEN, sku: SKU_SMALL, reason: "ok"});
}

function testTheCallableIsWiredTheWayTheSpecSays() {
  // index.js при загрузке поднимает firebase-admin, поэтому проверяется исходник — та же техника,
  // что в deferred-actions.test.js. Путь денег целиком исполняется в purchase-settlement.test.js;
  // здесь только договор обвязки: свой предел инстансов, делегирование, никакого `increment`.
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  const start = source.indexOf("exports.verifyPurchase = onCall(MONETARY_FUNCTION_OPTIONS");
  assert.ok(start > 0, "verifyPurchase объявлен со своим пределом инстансов");
  assert.match(source, /const MONETARY_FUNCTION_OPTIONS = \{\s*\.\.\.FUNCTION_OPTIONS,\s*maxInstances: [2-9]/);

  const body = source.slice(start, source.indexOf("\nexports.", start + 1));
  assert.ok(body.includes("settlePurchase("), "вызов делегирует purchase-settlement.js");
  assert.ok(!body.includes("increment("), "золото — абсолютом, не increment");
}

testAVerifiedUnseenTokenIsCreditedFromTheServerTable();
testTheSameTokenAgainReplaysTheStoredAnswerAndMovesNothing();
testAReplayOfARecordWithoutAStoredResponseRebuildsItFromTheRecord();
testASettlementThatIsNotCreditedIsRefusedRatherThanReplayed();
testAPendingPurchaseGrantsNothingAndIsNotAnError();
testACanceledPurchaseIsRefused();
testATokenPlayDoesNotKnowIsRefused();
testAnUnreachablePlayIsNotAVerdictAndTheClientRetries();
testAClaimedSkuThatDiffersFromPlaysIsRefused();
testAnUnsoldSkuIsRefusedRatherThanCreditedAtZero();
testATokenSettledByAnotherAccountIsRefusedWithoutLeakingItsAnswer();
testAPurchaseMarkedForAnotherAccountIsRefused();
testAConsumedTokenWithoutASettlementIsNotOursToCredit();
testAMissingConstantsDocumentCreditsBootstrapDefaultsAndAuditsVersionZero();
testATestPurchaseIsCreditedAndFlaggedInTheAudit();
testAChangedTableAppliesToTheNextPurchaseAndEarlierAuditsKeepTheirVersion();
testEveryRefusalCarriesAMachineReadableReasonCode();
testTheSettlementIdIsTheSha256OfTheTokenAndTheRawTokenIsStoredNowhere();
testTheRecordsNameWhatTheAuditNeeds();
testThePlayAnswerIsNormalisedBeforeItIsJudged();
testARequestWithoutATokenOrWithAnOddSkuIsRefusedBeforePlayIsAsked();
testTheCallableIsWiredTheWayTheSpecSays();

console.log("purchase-verification.test.js OK");
