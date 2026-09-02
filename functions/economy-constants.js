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
 * Не всё из таблицы уезжает на устройство: раздел `goldPacks` — размеры паков золота — знание
 * сервера, и вызову синхронизации отдаётся белый список ({@link clientEconomyConstants}).
 *
 * Чистый модуль: тестируется без firebase-admin.
 */

const DEFAULTS = require("../config/economy-constants.json");
// Форма SKU объявлена там, где решается, что по нему начислять: одно определение на оба модуля.
const {SKU_PATTERN} = require("./purchase-verification");

/**
 * Документ таблицы в Firestore.
 *
 * Имя объявлено один раз: его читает вызов синхронизации и называет в аудите каждое начисление за
 * деньги. Два написания разошлись бы ровно в тот день, когда аудит понадобился бы.
 */
const ECONOMY_CONSTANTS_DOC = "configs/economy";

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
  // Только настоящие числа: `Number(null)` — ноль, а нулевая ступень — бесплатный слот.
  const rungs = value.map((rung) => (typeof rung === "number" && Number.isFinite(rung) ? Math.floor(rung) : NaN));
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
 * Сумма за пак золота: только целое число от единицы.
 *
 * Строже, чем {@link nonNegativeInt}: тот принимает `null` и пустую строку за ноль, что для потолка
 * зарядов терпимо, а для денег нет. Ноль — тоже испорченная запись, а не цена: пак за ноль золота
 * означал бы, что игрок заплатил и получил ничего, и ответ «начислено» ему был бы ложью. Всё
 * испорченное откатывается к начальному размеру.
 */
function packAmount(value, fallback) {
  return typeof value === "number" && Number.isInteger(value) && value >= 1 ? value : fallback;
}

/**
 * Паки золота: SKU → золота за единицу покупки.
 *
 * Единственное место, где пак имеет размер. Устройство знает SKU, но не стоимость, и таблица
 * уезжает к нему без этого раздела: сумму называет сервер после проверки чека у Play, а не клиент
 * в запросе.
 *
 * Известные паки присутствуют всегда — испорченная запись откатывается к начальной, а не к нулю.
 * Пак, которого в начальных значениях нет, принимается, если он правильной формы: так SKU можно
 * завести на сервере до релиза клиента. SKU, которого в таблице нет, не продаётся — проверка чека
 * его отклонит, а не начислит ноль.
 */
function goldPacks(value) {
  // Массив — не таблица: его индексы «0», «1» стали бы SKU-призраками.
  const source = value && typeof value === "object" && !Array.isArray(value) ? value : {};
  const packs = {};
  for (const [sku, amount] of Object.entries(DEFAULTS.goldPacks)) {
    packs[sku] = packAmount(source[sku], amount);
  }
  for (const [sku, amount] of Object.entries(source)) {
    if (Object.prototype.hasOwnProperty.call(packs, sku) || !SKU_PATTERN.test(sku)) continue;
    const parsed = packAmount(amount, null);
    if (parsed !== null) packs[sku] = parsed;
  }
  return packs;
}

/**
 * Приводит документ к рабочей таблице.
 *
 * Испорченный или отсутствующий документ вырождается в начальные значения, а не в нулевые потолки:
 * нулевой потолок запер бы каждый аккаунт, и починка потребовала бы того самого релиза, ради
 * отсутствия которого всё и затевалось.
 *
 * @param document содержимое `configs/economy`, каким его вернул Firestore. Может быть чем угодно.
 * @param fallbackVersion версия для сохранённого документа без своего числа — время его последней
 *   записи. Иначе две правки подряд без версии обе были бы «единицей», и вторая до устройств не
 *   доехала бы никогда.
 */
function readEconomyConstants(document, fallbackVersion) {
  const source = document && typeof document === "object" ? document : {};
  // Настоящая таблица начинается с единицы. Документ без версии — оператор забыл её поставить —
  // всё равно обязан побеждать загрузочную копию: у той версия ноль, и клиент, присылающий ноль,
  // иначе получал бы «то же самое» вечно, пока сервер уже списывает по новой таблице.
  const stored = document && typeof document === "object";
  const ownVersion = nonNegativeInt(source.version, 0);
  const version = ownVersion > 0 ? ownVersion :
    stored ? Math.max(1, nonNegativeInt(fallbackVersion, 1)) : DEFAULTS.version;
  return {
    version,
    standard: chargeRules(source.standard, DEFAULTS.standard),
    plasma: chargeRules(source.plasma, DEFAULTS.plasma),
    activityPrices: activityPrices(source.activityPrices),
    goldPacks: goldPacks(source.goldPacks),
    clockSkewToleranceMs: nonNegativeInt(source.clockSkewToleranceMs, DEFAULTS.clockSkewToleranceMs),
    auditEnabled: typeof source.auditEnabled === "boolean" ?
      source.auditEnabled : DEFAULTS.auditEnabled,
  };
}

/**
 * Что из таблицы уезжает на устройство.
 *
 * Белый список, а не «всё, кроме»: раздел, добавленный на сервер завтра, по умолчанию остаётся на
 * сервере. Сегодня за бортом `goldPacks`. Клиент, знающий размер пака, ничем не опаснее клиента,
 * его не знающего — начисляет всё равно сервер, — но таблица цен на устройстве стала бы обещанием,
 * которое сервер не давал.
 *
 * @param constants таблица, уже приведённая {@link readEconomyConstants}.
 */
function clientEconomyConstants(constants) {
  const source = constants && typeof constants === "object" ? constants : readEconomyConstants(null);
  return {
    version: source.version,
    standard: source.standard,
    plasma: source.plasma,
    activityPrices: source.activityPrices,
    clockSkewToleranceMs: source.clockSkewToleranceMs,
    auditEnabled: source.auditEnabled,
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
  ECONOMY_CONSTANTS_DOC,
  POINTS_PER_CHARGE,
  SKU_PATTERN,
  activityPrice,
  clientEconomyConstants,
  readEconomyConstants,
  slotPrice,
};
