"use strict";

const assert = require("assert");
const {
  ACTIVITY_KINDS,
  DEFAULTS,
  POINTS_PER_CHARGE,
  activityPrice,
  readEconomyConstants,
  slotPrice,
} = require("./economy-constants");

function testAMissingDocumentDegradesToTheInitialValues() {
  // Нулевой потолок запер бы каждый аккаунт разом, и починка потребовала бы релиза — ровно того,
  // ради отсутствия которого таблицу и вынесли на сервер.
  const constants = readEconomyConstants(null);

  assert.strictEqual(constants.standard.maxOwned, 10);
  assert.strictEqual(constants.plasma.maxOwned, 3);
  assert.deepStrictEqual(constants.activityPrices, DEFAULTS.activityPrices);
}

function testAMalformedDocumentDegradesFieldByField() {
  // Одно испорченное число не должно отменять все остальные: документ правят руками.
  const constants = readEconomyConstants({
    version: 7,
    standard: {maxOwned: "не число", regenMs: 1800000},
    activityPrices: {TOURNAMENT: "бесплатно"},
  });

  assert.strictEqual(constants.version, 7);
  assert.strictEqual(constants.standard.maxOwned, 10, "испорченный потолок — начальный потолок");
  assert.strictEqual(constants.standard.regenMs, 1800000, "а соседнее верное значение принято");
  assert.strictEqual(constants.activityPrices.TOURNAMENT, 500, "бесплатный турнир не назначается");
}

function testAPriceThatIsMissingIsFilledRatherThanFree() {
  const constants = readEconomyConstants({activityPrices: {ORDINARY_LESSON: 40}});

  assert.strictEqual(constants.activityPrices.ORDINARY_LESSON, 40);
  for (const kind of ACTIVITY_KINDS) {
    assert.ok(constants.activityPrices[kind] > 0, `${kind} остался без цены`);
  }
}

function testAnUnknownActivityIsChargedAtTheDearestKnownRate() {
  // Незнакомый вид — это либо новый вид, до которого сервер не обновили, либо выдумка клиента.
  // Пропустить оплату нельзя ни в том, ни в другом случае.
  const constants = readEconomyConstants(null);

  assert.strictEqual(activityPrice(constants, "ЧТО-ТО НОВОЕ"), 500);
  assert.strictEqual(activityPrice(constants, "ORDINARY_LESSON"), 33);
}

function testALadderShorterThanTheCeilingRepeatsItsLastRung() {
  // Иначе слот сверх лестницы стоил бы ноль, и поднять потолок значило бы раздать заряды даром.
  assert.strictEqual(slotPrice(DEFAULTS.standard, 4), 20000);
  assert.strictEqual(slotPrice(DEFAULTS.standard, 11), 20000);
  assert.strictEqual(slotPrice(DEFAULTS.standard, 0), 1000);
}

function testTheFullTankBuysTwoTournaments() {
  // Соотношение и есть модель: полный бак — тысяча очков, турнир — пятьсот.
  const tank = DEFAULTS.standard.maxOwned * POINTS_PER_CHARGE;

  assert.strictEqual(tank, 1000);
  assert.strictEqual(tank / DEFAULTS.activityPrices.TOURNAMENT, 2);
  assert.strictEqual(Math.floor(tank / DEFAULTS.activityPrices.FINAL_EXAM), 3);
  assert.strictEqual(Math.floor(tank / DEFAULTS.activityPrices.ORDINARY_LESSON), 30);
}

function testThePlasmaLadderCostsSixGoldInTotal() {
  const total = [0, 1, 2].reduce((sum, owned) => sum + slotPrice(DEFAULTS.plasma, owned), 0);

  assert.strictEqual(total, 6);
}

function testTheServerDefaultsAreTheSharedFileVerbatim() {
  // Загрузочная копия на устройстве считает по этому же файлу (EconomyConstantsParityTest).
  // Разойдись они — игрок на новом устройстве увидел бы одну цену, а списали бы другую.
  const shared = require("../config/economy-constants.json");

  assert.deepStrictEqual(readEconomyConstants(null), {
    version: shared.version,
    standard: shared.standard,
    plasma: shared.plasma,
    activityPrices: shared.activityPrices,
    clockSkewToleranceMs: shared.clockSkewToleranceMs,
    auditEnabled: shared.auditEnabled,
  });
}

testTheServerDefaultsAreTheSharedFileVerbatim();
testAMissingDocumentDegradesToTheInitialValues();
testAMalformedDocumentDegradesFieldByField();
testAPriceThatIsMissingIsFilledRatherThanFree();
testAnUnknownActivityIsChargedAtTheDearestKnownRate();
testALadderShorterThanTheCeilingRepeatsItsLastRung();
testTheFullTankBuysTwoTournaments();
testThePlasmaLadderCostsSixGoldInTotal();

console.log("economy-constants.test.js OK");
