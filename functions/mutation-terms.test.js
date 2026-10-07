"use strict";

/**
 * Чистая половина сверки согласия: проекция таблицы, чтение конверта, вердикт.
 *
 * Приёмниковая половина (вызов `submitMutation` против эмулятора) живёт в
 * `submit-mutation-terms.test.js` — там же причина, почему эти сюиты разделены.
 */

const assert = require("assert");
const {ACTIVITY_KINDS} = require("./economy-constants");
const {TERMS_CONSENT_DETAIL, payableTermsProjection, readCapturedTerms, termsVerdict} = require("./mutation-terms");

/** Текущая таблица для примеров: всё по начальным значениям, версия 7. */
function currentTable() {
  return {
    version: 7,
    activityPrices: {ORDINARY_LESSON: 33, ARENA: 100, THEME_TEST: 150, FINAL_EXAM: 200, TOURNAMENT: 500},
    standard: {
      maxOwned: 10,
      regenMs: 1800000,
      priceLadder: [100, 200, 300],
      currency: "STANDARD_CHARGE_POINTS",
      requiresSettledAccount: false,
      premiumRegenDivisor: 1,
    },
    plasma: {
      maxOwned: 3,
      regenMs: 3600000,
      priceLadder: [10, 20],
      currency: "GOLD",
      requiresSettledAccount: false,
      premiumRegenDivisor: 1,
    },
  };
}

/** Согласие, снятое с той же таблицы: сверка обязана признать его достаточным. */
function capturedOf(table) {
  return JSON.parse(JSON.stringify({version: table.version, activityPrices: table.activityPrices, standard: table.standard, plasma: table.plasma}));
}

function testAProjectionCarriesOnlyPayableFields() {
  const table = currentTable();
  table.clockSkewToleranceMs = 45000;
  table.auditEnabled = true;
  table.goldPacks = {small: {sku: "sku_small", gold: 100}};

  const projection = payableTermsProjection(table);

  assert.deepStrictEqual(Object.keys(projection).sort(), ["activityPrices", "plasma", "standard", "version"]);
  assert.strictEqual(projection.version, 7);
  assert.deepStrictEqual(Object.keys(projection.activityPrices).sort(), [...ACTIVITY_KINDS].sort());
  assert.strictEqual(projection.standard.maxOwned, 10);
  assert.deepStrictEqual(projection.plasma.priceLadder, [10, 20]);
}

function testAProjectionSurvivesADegradedTable() {
  // readEconomyConstants нормализует таблицу до проекции, но модуль отвечает за себя:
  // испорченный документ не должен превратить сверку в сравнение с undefined.
  const projection = payableTermsProjection(null);
  assert.strictEqual(projection.version, 0);
  assert.strictEqual(projection.activityPrices.ORDINARY_LESSON, 0);
  assert.strictEqual(projection.standard.premiumRegenDivisor, 1);

  const partial = payableTermsProjection({version: "не число", activityPrices: {ORDINARY_LESSON: "тоже"}});
  assert.strictEqual(partial.version, 0);
  assert.strictEqual(partial.activityPrices.ORDINARY_LESSON, 0, "испорченная цена читается нулём, а не пропуском");
}

function testACapturedVerbatimProjectionIsSufficient() {
  const table = currentTable();
  const verdict = termsVerdict(readCapturedTerms({capturedTerms: capturedOf(table)}).captured, payableTermsProjection(table));

  assert.strictEqual(verdict.sufficient, true);
  assert.strictEqual(verdict.change, null);
}

function testAVersionBumpAloneIsNotAChange() {
  // Правка неплатной ручки поднимает версию, но цены не трогает: переспрос по ней приучал бы
  // игрока соглашаться не глядя — ровно тот вывод, что и у клиентского TermsConsentGate.
  const table = currentTable();
  const captured = capturedOf(table);
  captured.version = 8;

  const verdict = termsVerdict(captured, payableTermsProjection(table));

  assert.strictEqual(verdict.sufficient, true, "версия без платных правок — согласие достаточно");
}

function testAChangedPriceIsNamedByKind() {
  const table = currentTable();
  const captured = capturedOf(table);
  table.activityPrices.ORDINARY_LESSON = 40;

  const verdict = termsVerdict(captured, payableTermsProjection(table));

  assert.strictEqual(verdict.sufficient, false);
  assert.deepStrictEqual(verdict.change.prices, {ORDINARY_LESSON: {from: 33, to: 40}});
  assert.deepStrictEqual(verdict.change.chargeRules, {}, "правила не тронуты — называются только цены");
}

function testChangedChargeRulesAreNamedByKind() {
  const table = currentTable();
  const captured = capturedOf(table);
  table.plasma.maxOwned = 5;
  table.standard.currency = "GOLD";

  const verdict = termsVerdict(captured, payableTermsProjection(table));

  assert.strictEqual(verdict.sufficient, false);
  assert.deepStrictEqual(Object.keys(verdict.change.prices), [], "цены не тронуты");
  assert.deepStrictEqual(Object.keys(verdict.change.chargeRules).sort(), ["plasma", "standard"]);
  assert.strictEqual(verdict.change.chargeRules.plasma.from.maxOwned, 3);
  assert.strictEqual(verdict.change.chargeRules.plasma.to.maxOwned, 5);
}

function testALadderRungCountsAsAChange() {
  const table = currentTable();
  const captured = capturedOf(table);
  table.standard.priceLadder = [100, 200, 350];

  const verdict = termsVerdict(captured, payableTermsProjection(table));

  assert.strictEqual(verdict.sufficient, false);
  assert.ok(verdict.change.chargeRules.standard, "ступень лестницы — платное поле");
}

function testNoConsentAtAllIsInsufficientWithoutAChange() {
  // Назвать изменение нечем: игрок соглашается впервые. Нулевая цена от отсутствия согласия
  // не отличается — различает их только captured === null.
  const table = currentTable();
  const verdict = termsVerdict(null, payableTermsProjection(table));

  assert.strictEqual(verdict.sufficient, false);
  assert.strictEqual(verdict.change, null);
}

function testAnAbsentFieldMeansNoConsentNotAnEmptyOne() {
  const verdict = readCapturedTerms({});
  assert.strictEqual(verdict.valid, true, "поле необязательное — решает реестр платных операций");
  assert.strictEqual(verdict.captured, null);
}

function testACapturedTermsMustBeComplete() {
  const table = currentTable();
  const missingPrice = capturedOf(table);
  delete missingPrice.activityPrices.TOURNAMENT;
  assert.strictEqual(readCapturedTerms({capturedTerms: missingPrice}).valid, false, "согласие без цены вида сверять нельзя");

  const missingRuleField = capturedOf(table);
  delete missingRuleField.plasma.currency;
  assert.strictEqual(readCapturedTerms({capturedTerms: missingRuleField}).valid, false);

  const negativePrice = capturedOf(table);
  negativePrice.activityPrices.ARENA = -1;
  assert.strictEqual(readCapturedTerms({capturedTerms: negativePrice}).valid, false, "отрицательной цены не бывает");

  const zeroDivisor = capturedOf(table);
  zeroDivisor.standard.premiumRegenDivisor = 0;
  assert.strictEqual(readCapturedTerms({capturedTerms: zeroDivisor}).valid, false, "делитель меньше единицы не бывает");

  assert.strictEqual(readCapturedTerms({capturedTerms: "не JSON"}).valid, false);
  assert.strictEqual(readCapturedTerms({capturedTerms: 42}).valid, false, "не объект — не согласие");
}

function testACapturedTermsRidesAsAJsonString() {
  // Из очереди конверт приезжает со строками там, где клиент клал объекты; согласие — не
  // исключение, и трактовка та же, что у parseMutationPayload.
  const table = currentTable();
  const verdict = readCapturedTerms({capturedTerms: JSON.stringify(capturedOf(table))});

  assert.strictEqual(verdict.valid, true);
  assert.strictEqual(termsVerdict(verdict.captured, payableTermsProjection(table)).sufficient, true);
}

function testExtraFieldsInTheEnvelopeAreNotConsent() {
  // Согласие читается из своего поля, а не из первого попавшегося: лишние поля конверта —
  // не способ передать условия мимо сверки.
  const table = currentTable();
  const envelope = capturedOf(table);
  const verdict = readCapturedTerms({payload: envelope});

  assert.strictEqual(verdict.valid, true);
  assert.strictEqual(verdict.captured, null, "capturedTerms нет — согласия нет");
}

function testTheDetailMarkIsStable() {
  // Клиентская половина различает ветви по этому значению: переименование ломает контракт
  // молча. Строка обязана совпадать с тем, под каким именем ветвь названа на клиенте.
  assert.strictEqual(TERMS_CONSENT_DETAIL, "terms-consent-required");
}

testAProjectionCarriesOnlyPayableFields();
testAProjectionSurvivesADegradedTable();
testACapturedVerbatimProjectionIsSufficient();
testAVersionBumpAloneIsNotAChange();
testAChangedPriceIsNamedByKind();
testChangedChargeRulesAreNamedByKind();
testALadderRungCountsAsAChange();
testNoConsentAtAllIsInsufficientWithoutAChange();
testAnAbsentFieldMeansNoConsentNotAnEmptyOne();
testACapturedTermsMustBeComplete();
testACapturedTermsRidesAsAJsonString();
testExtraFieldsInTheEnvelopeAreNotConsent();
testTheDetailMarkIsStable();

console.log("mutation-terms.test.js OK");
