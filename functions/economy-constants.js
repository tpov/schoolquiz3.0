"use strict";

/**
 * Таблица настроек экономики зарядов.
 *
 * Смысл в том, что ни одно число отсюда не требует релиза: подкрутить цену турнира или потолок
 * зарядов должно быть решением на сервере. Таблица лежит одним документом в `configs/economy` —
 * коллекция закрыта для клиентов правилами, и читает её только сервер, как уже читает
 * `configs/nickname_policy` и `configs/arena_review`.
 *
 * Начальные значения продублированы в `config/economy-constants.json` и в Kotlin
 * (`EconomyConstants.BOOTSTRAP`), потому что устройство, которое ещё ни разу не синхронизировалось,
 * обязано запуститься. Файл — единственный источник для обеих сторон, и оба набора тестов его
 * читают: разойдутся — упадёт сборка.
 *
 * Чистый модуль: тестируется без firebase-admin.
 */

const DEFAULTS = require("../config/economy-constants.json");

/** Очков в одном заряде. Не настройка: прейскурант выражен в очках. */
const POINTS_PER_CHARGE = 100;

/** Виды попыток, которым обязана быть назначена цена. */
const ACTIVITY_KINDS = [
  "ORDINARY_LESSON",
  "ARENA",
  "THEME_TEST",
  "FINAL_EXAM",
  "TOURNAMENT",
];

/**
 * Целое неотрицательное число из документа — или начальное значение.
 *
 * Только настоящее число: `Number(null)`, `Number("")`, `Number(false)` и `Number([])` — это ноль,
 * и прежняя версия принимала их за него. Ноль здесь — не «пусто», а нулевой потолок, который
 * запирает каждый аккаунт, или бесплатная попытка. Пустое поле должно значить «как было».
 */
function nonNegativeInt(value, fallback) {
  if (typeof value !== "number" || !Number.isFinite(value)) return fallback;
  const number = Math.floor(value);
  return number >= 0 ? number : fallback;
}


function ladder(value, fallback) {
  if (!Array.isArray(value) || value.length === 0) return fallback;
  const rungs = value.map((rung) => Math.floor(Number(rung)));
  // Одна испорченная ступень делает бессмысленной всю лестницу: цены соседних слотов связаны.
  return rungs.every((rung) => Number.isFinite(rung) && rung >= 0) ? rungs : fallback;
}

function chargeRules(value, fallback) {
  const source = value && typeof value === "object" ? value : {};
  return {
    maxOwned: nonNegativeInt(source.maxOwned, fallback.maxOwned),
    regenMs: nonNegativeInt(source.regenMs, fallback.regenMs),
    priceLadder: ladder(source.priceLadder, fallback.priceLadder),
    currency: typeof source.currency === "string" && source.currency ?
      source.currency : fallback.currency,
    requiresSettledAccount: typeof source.requiresSettledAccount === "boolean" ?
      source.requiresSettledAccount : fallback.requiresSettledAccount,
  };
}

function activityPrices(value) {
  const source = value && typeof value === "object" ? value : {};
  const prices = {};
  for (const kind of ACTIVITY_KINDS) {
    // Отсутствующая цена — это бесплатный турнир, поэтому дыра затыкается начальным значением,
    // а не нулём и не отказом: отказ запер бы всех игроков разом.
    prices[kind] = nonNegativeInt(source[kind], DEFAULTS.activityPrices[kind]);
  }
  return prices;
}

/**
 * Приводит документ к рабочей таблице.
 *
 * Испорченный или отсутствующий документ вырождается в начальные значения, а не в нулевые потолки:
 * нулевой потолок запер бы каждый аккаунт, и починка потребовала бы того самого релиза, ради
 * отсутствия которого всё и затевалось.
 *
 * @param document содержимое `configs/economy`, каким его вернул Firestore. Может быть чем угодно.
 */
function readEconomyConstants(document) {
  const source = document && typeof document === "object" ? document : {};
  // Настоящая таблица начинается с единицы. Документ без версии — оператор забыл её поставить —
  // всё равно обязан побеждать загрузочную копию: у той версия ноль, и клиент, присылающий ноль,
  // иначе получал бы «то же самое» вечно, пока сервер уже списывает по новой таблице.
  const stored = document && typeof document === "object";
  const version = Math.max(stored ? 1 : 0, nonNegativeInt(source.version, DEFAULTS.version));
  return {
    version,

    standard: chargeRules(source.standard, DEFAULTS.standard),
    plasma: chargeRules(source.plasma, DEFAULTS.plasma),
    activityPrices: activityPrices(source.activityPrices),
    clockSkewToleranceMs: nonNegativeInt(source.clockSkewToleranceMs, DEFAULTS.clockSkewToleranceMs),
    auditEnabled: typeof source.auditEnabled === "boolean" ?
      source.auditEnabled : DEFAULTS.auditEnabled,
  };
}

/**
 * Сколько очков стоит попытка этого вида.
 *
 * Вид решает сервер, а не устройство: если вид называет клиент, а сервер по названному списывает,
 * то самый дешёвый вид — это тот, который назовёт любой клиент.
 */
function activityPrice(constants, activityKind) {
  const prices = (constants && constants.activityPrices) || DEFAULTS.activityPrices;
  const price = prices[activityKind];
  // Незнакомый вид — не повод пропустить оплату: берём как за самое дорогое известное.
  return Number.isFinite(price) ? price : Math.max(...Object.values(prices));
}

/**
 * Цена слота, когда на руках уже `owned` зарядов.
 *
 * Лестница короче потолка — не ошибка: последняя ступень повторяется. Поднять потолок на сервере
 * проще, чем помнить, что вместе с ним надо удлинить и лестницу, а молчаливый ноль за слот сверх
 * лестницы был бы бесплатным зарядом.
 */
function slotPrice(rules, owned) {
  const rungs = (rules && rules.priceLadder) || [];
  if (rungs.length === 0) return Number.POSITIVE_INFINITY;
  const index = Math.min(Math.max(0, Math.floor(Number(owned) || 0)), rungs.length - 1);
  return rungs[index];
}

module.exports = {
  ACTIVITY_KINDS,
  DEFAULTS,
  POINTS_PER_CHARGE,
  activityPrice,
  readEconomyConstants,
  slotPrice,
};
