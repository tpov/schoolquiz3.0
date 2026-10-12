"use strict";

const assert = require("assert");
const {
  ACTIVITY_KINDS,
  DEFAULTS,
  ECONOMY_CONSTANTS_DOC,
  POINTS_PER_CHARGE,
  SKU_PATTERN,
  activityPrice,
  clientEconomyConstants,
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
  const shared = require("./config/economy-constants.json");

  assert.deepStrictEqual(readEconomyConstants(null), {
    version: shared.version,
    standard: shared.standard,
    plasma: shared.plasma,
    activityPrices: shared.activityPrices,
    goldPacks: shared.goldPacks,
    boxPacks: shared.boxPacks,
    seasons: shared.seasons,
    premiumProducts: {},
    clockSkewToleranceMs: shared.clockSkewToleranceMs,
    auditEnabled: shared.auditEnabled,
  });
}

function testBoxPacksAreReadLikeGoldPacksAndStayServerKnowledge() {
  // Коробочные товары (история 3.3): тот же язык, что у золотых паков. Ноль — «не продаётся»,
  // а не бесплатная коробка; посторонний SKU правильной формы принимается — так товар заводится
  // на сервере до релиза клиента.
  const constants = readEconomyConstants({
    boxPacks: {
      box_pack_single: 1,
      box_pack_triple: 3,
      box_pack_zero: 0,
      box_pack_fraction: 1.5,
      "Bad Sku": 2,
    },
  });
  assert.strictEqual(constants.boxPacks.box_pack_single, 1);
  assert.strictEqual(constants.boxPacks.box_pack_triple, 3);
  assert.strictEqual(constants.boxPacks.box_pack_zero, undefined, "ноль — испорченная запись, а не цена");
  assert.strictEqual(constants.boxPacks.box_pack_fraction, undefined);
  assert.strictEqual(constants.boxPacks["Bad Sku"], undefined);
  // Серверное знание: на устройство раздел не уезжает, как goldPacks.
  assert.strictEqual(Object.prototype.hasOwnProperty.call(clientEconomyConstants(constants), "boxPacks"), false);
  // Пустая таблица и испорченный документ вырождаются в пустые начальные значения, не в отказ.
  assert.deepStrictEqual(readEconomyConstants(null).boxPacks, {});
  assert.deepStrictEqual(readEconomyConstants({boxPacks: "нет"}).boxPacks, {});
}

function testGoldPacksAreReadStrictlyAndFallBackToTheInitialSizes() {
  // Деньги: то, что для потолка зарядов сходит за ноль (`null`, пустая строка), для пака —
  // испорченная запись. Ноль здесь означал бы «заплатил и получил ничего».
  const constants = readEconomyConstants({
    goldPacks: {
      gold_pack_small: 12,
      gold_pack_medium: null,
      gold_pack_large: "150",
      gold_pack_xl: 400,
      "Bad Sku": 5,
      gold_pack_negative: -1,
      gold_pack_fraction: 2.5,
      gold_pack_zero: 0,
    },
  });

  assert.strictEqual(constants.goldPacks.gold_pack_small, 12, "верное значение принято");
  assert.strictEqual(constants.goldPacks.gold_pack_medium, 60, "null — начальный размер, не ноль");
  assert.strictEqual(constants.goldPacks.gold_pack_large, 150, "строка — не число, начальный размер");
  assert.strictEqual(constants.goldPacks.gold_pack_xl, 400, "новый пак правильной формы принят");
  assert.strictEqual("Bad Sku" in constants.goldPacks, false, "SKU не той формы отброшен");
  assert.strictEqual("gold_pack_negative" in constants.goldPacks, false);
  assert.strictEqual("gold_pack_fraction" in constants.goldPacks, false);
  assert.strictEqual("gold_pack_zero" in constants.goldPacks, false, "пак за ноль не назначается");

  // Ноль у известного пака — испорченная запись, не цена: игрок заплатил бы и получил ничего.
  const zero = readEconomyConstants({goldPacks: {gold_pack_large: 0}});
  assert.strictEqual(zero.goldPacks.gold_pack_large, 150);

  // Массив — не таблица: его индексы стали бы SKU-призраками «0» и «1».
  const array = readEconomyConstants({goldPacks: [10, 60]});
  assert.deepStrictEqual(array.goldPacks, DEFAULTS.goldPacks);
  assert.strictEqual("0" in array.goldPacks, false);
}

function testTheSkuShapeIsOneDefinitionSharedWithTheVerifier() {
  assert.strictEqual(SKU_PATTERN, require("./purchase-verification").SKU_PATTERN);
}

function testAMissingGoldPacksSectionKeepsEveryKnownPackOnSale() {
  assert.deepStrictEqual(readEconomyConstants({version: 3}).goldPacks, DEFAULTS.goldPacks);
  assert.deepStrictEqual(readEconomyConstants({goldPacks: "нет"}).goldPacks, DEFAULTS.goldPacks);
  assert.deepStrictEqual(Object.keys(DEFAULTS.goldPacks), ["gold_pack_small", "gold_pack_medium", "gold_pack_large"]);
}

function testTheClientNeverSeesThePackSizes() {
  // Размеры паков — знание сервера: сумму называет он после проверки чека, а не клиент в запросе.
  // Белый список, а не «всё, кроме»: раздел, добавленный завтра, тоже не уедет сам по себе.
  const shared = require("./config/economy-constants.json");
  const forClient = clientEconomyConstants(readEconomyConstants({goldPacks: {gold_pack_small: 999}}));

  assert.strictEqual("goldPacks" in forClient, false);
  assert.strictEqual("boxPacks" in forClient, false, "коробочные паки — тоже серверное знание");
  assert.deepStrictEqual(
    Object.keys(forClient).sort(),
    ["activityPrices", "auditEnabled", "clockSkewToleranceMs", "plasma", "premiumGoldOffers", "standard", "version"],
  );
  const {goldPacks, boxPacks, seasons, premiumProducts, ...withoutPacks} = shared;
  assert.ok(goldPacks, "в общем файле раздел есть");
  assert.ok(seasons, "раздел сезона в общем файле есть");
  assert.ok(premiumProducts && Object.keys(premiumProducts).length > 0,
    "золотой прейскурант в общем файле есть");
  assert.strictEqual("seasons" in clientEconomyConstants(readEconomyConstants(null)), false,
    "полосы выплат — знание сервера: выплата называется им, а не клиентом");
  assert.strictEqual("premiumProducts" in clientEconomyConstants(readEconomyConstants(null)), false,
    "сырая таблица премиума — знание сервера: уезжает готовый прейскурант premiumGoldOffers");
  assert.deepStrictEqual(clientEconomyConstants(readEconomyConstants(null)), withoutPacks);
  assert.strictEqual(ECONOMY_CONSTANTS_DOC, "configs/economy");
}

function testAnEmptyFieldMeansAsBeforeNotZero() {
  // Number(null), Number(""), Number(false), Number([]) — всё ноль, и ноль здесь запирает аккаунт.
  for (const empty of [null, "", false, [], "0", true]) {
    const constants = readEconomyConstants({standard: {maxOwned: empty}, activityPrices: {TOURNAMENT: empty}});
    assert.strictEqual(constants.standard.maxOwned, 10, `пустое ${JSON.stringify(empty)} стало потолком`);
    assert.strictEqual(constants.activityPrices.TOURNAMENT, 500, `пустое ${JSON.stringify(empty)} стало ценой`);
  }
}

function testAStoredTableAlwaysOutranksTheBootstrapCopy() {
  // Документ без версии обязан побеждать копию с версией ноль: иначе клиент, присылающий ноль,
  // получал бы «то же самое» вечно, пока сервер уже списывает по новой таблице.
  assert.strictEqual(readEconomyConstants({}).version, 1);
  assert.strictEqual(readEconomyConstants({version: 0}).version, 1);
  // Документ без своего числа получает время записи: две правки подряд — две разные версии.
  assert.strictEqual(readEconomyConstants({}, 1_700_000_000_000).version, 1_700_000_000_000);
  assert.strictEqual(readEconomyConstants({version: 7}, 1_700_000_000_000).version, 7, "своё число главнее");
  assert.strictEqual(readEconomyConstants({version: 7}).version, 7);
  assert.strictEqual(readEconomyConstants(null).version, DEFAULTS.version,
    "а отсутствующий документ — это и есть копия, с её версией");
}

function testAnEmptyRungIsNotAFreeSlot() {
  const constants = readEconomyConstants({standard: {priceLadder: [1000, null, 5000]}, plasma: {priceLadder: ["", 2, 3]}});
  assert.deepStrictEqual(constants.standard.priceLadder, DEFAULTS.standard.priceLadder);
  assert.deepStrictEqual(constants.plasma.priceLadder, DEFAULTS.plasma.priceLadder);
}

function testPremiumRegeneratesFasterOnlyIfTheTableSaysSo() {
  const {regenMsFor} = require("./economy-constants");
  const stock = readEconomyConstants(null);
  assert.strictEqual(regenMsFor(stock.standard, true), stock.standard.regenMs, "делитель 1 — не быстрее");
  const twice = readEconomyConstants({standard: {premiumRegenDivisor: 2}});
  assert.strictEqual(regenMsFor(twice.standard, true), stock.standard.regenMs / 2);
  assert.strictEqual(regenMsFor(twice.standard, false), stock.standard.regenMs, "без премиума — как есть");
  const zero = readEconomyConstants({standard: {premiumRegenDivisor: 0}});
  assert.strictEqual(zero.standard.premiumRegenDivisor, 1, "ноль не делитель");
}

testPremiumRegeneratesFasterOnlyIfTheTableSaysSo();
testAnEmptyRungIsNotAFreeSlot();
testAnEmptyFieldMeansAsBeforeNotZero();
testAStoredTableAlwaysOutranksTheBootstrapCopy();
testTheServerDefaultsAreTheSharedFileVerbatim();
testAMissingDocumentDegradesToTheInitialValues();
testAMalformedDocumentDegradesFieldByField();
testAPriceThatIsMissingIsFilledRatherThanFree();
testAnUnknownActivityIsChargedAtTheDearestKnownRate();
testALadderShorterThanTheCeilingRepeatsItsLastRung();
testTheFullTankBuysTwoTournaments();
testThePlasmaLadderCostsSixGoldInTotal();
testGoldPacksAreReadStrictlyAndFallBackToTheInitialSizes();
testTheSkuShapeIsOneDefinitionSharedWithTheVerifier();
testAMissingGoldPacksSectionKeepsEveryKnownPackOnSale();
testTheClientNeverSeesThePackSizes();

function testSeasonBandsCoverEveryRankExactlyOnceOrFallBack() {
  // Полосы сезона (история 5.2): свойство таблицы — каждое место 1..100 ровно в одной полосе.
  // Испорченная таблица откатывается к начальной: нулевая полоса — сезон, за который никто не
  // получил ничего.
  const stock = readEconomyConstants(null);
  const seen = new Set();
  for (const [from, to, boxes] of stock.seasons.leaderboardBands) {
    assert.strictEqual(boxes > 0, true);
    for (let rank = from; rank <= to; rank++) {
      assert.strictEqual(seen.has(rank), false, `место ${rank} в двух полосах`);
      seen.add(rank);
    }
  }
  assert.deepStrictEqual([...seen.keys()].sort((a, b) => a - b), Array.from({length: 100}, (_, i) => i + 1));
  const dictated = stock.seasons.leaderboardBands.map((row) => row.join(":")).join(",");
  assert.strictEqual(dictated, "1:1:500,2:2:250,3:3:125,4:5:70,6:10:50,11:30:25,31:50:15,51:100:5",
    "лестница диктуется спекой, а не удобством правки");
  const broken = readEconomyConstants({seasons: {leaderboardBands: [[1, 40, 500], [50, 100, 5]]}});
  assert.deepStrictEqual(broken.seasons.leaderboardBands, stock.seasons.leaderboardBands,
    "дыра между полосами возвращает начальную таблицу");
  const overlapping = readEconomyConstants({seasons: {leaderboardBands: [[1, 60, 1], [50, 100, 5]]}});
  assert.deepStrictEqual(overlapping.seasons.leaderboardBands, stock.seasons.leaderboardBands);
  const shortTail = readEconomyConstants({seasons: {leaderboardBands: [[1, 1, 500], [2, 99, 5]]}});
  assert.deepStrictEqual(shortTail.seasons.leaderboardBands, stock.seasons.leaderboardBands);
  const zeroBand = readEconomyConstants({seasons: {leaderboardBands: [[1, 99, 500], [100, 100, -5]]}});
  assert.deepStrictEqual(zeroBand.seasons.leaderboardBands, stock.seasons.leaderboardBands);
  const empty = readEconomyConstants({seasons: {}});
  assert.deepStrictEqual(empty.seasons.leaderboardBands, stock.seasons.leaderboardBands);
}

testBoxPacksAreReadLikeGoldPacksAndStayServerKnowledge();
testSeasonBandsCoverEveryRankExactlyOnceOrFallBack();

console.log("economy-constants.test.js OK");
