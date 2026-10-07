"use strict";

/**
 * Серверная половина CAP-16/AD-5: сверка платных условий согласия в приёмнике мутаций.
 *
 * Клиентская половина (история 2.4) захватывает `PayableTerms` — платную проекцию таблицы
 * `configs/economy`: прейскурант попыток и правила обоих видов зарядов — и требует свежего
 * согласия, когда захваченные условия разошлись с текущими. Здесь та же сверка повторяется
 * сервером: новая мутация платной операции обязана назвать условия, под которыми игрок
 * согласился, и отклоняется, если платные поля с тех пор изменились. Скрытая переоценка
 * согласованной операции — вычисление эффекта по нынешней таблице без нового согласия —
 * и есть то, что AD-5 запрещает.
 *
 * Сверяются только платные поля. Номер версии и неплатные ручки таблицы (`clockSkewToleranceMs`,
 * `auditEnabled`) вердикт не меняют — ровно как в клиентском `TermsConsentGate`: правка
 * неплатной ручки не повод переспрашивать согласие по неизменным ценам. Поэтому проекция здесь
 * отбирает поля поимённо, а не передаёт таблицу целиком.
 *
 * Модуль чистый — тестируется без firebase-admin; таблицу внутрь приносит вызывающий
 * (`readEconomyConstantsDocument` в `index.js`).
 */

const {ACTIVITY_KINDS} = require("./economy-constants");

/**
 * Пометка, по которой клиент узнаёт именно отказ по согласию с условиями.
 *
 * Код ответа — FAILED_PRECONDITION: согласия нет или оно устарело, и повтор этого не изменит,
 * пока игрок не согласится заново, — отказ, а не состязание. Вторая пометка поверх
 * FAILED_PRECONDITION нужна по той же причине, что и `PRECONDITION_PENDING_DETAIL`: голый код
 * в этом проекте означает карантин и откат локального изменения (AD-28), а ветвь «покажи
 * игроку новые условия и жди согласия» — не карантин. Детали — объект с полем `code`
 * (как у `VERSION_CONFLICT_DETAIL`): решение принимается по полю, а не по разбору текста
 * (AD-15), и рядом с `code` едет `change` — что именно изменилось, уже разобранное.
 *
 * Значение обязано совпадать с тем, чем ветвь назовёт себя клиентская половина.
 */
const TERMS_CONSENT_DETAIL = "terms-consent-required";

/** Поля правил заряда, требующие согласия: состав клиентского `ChargeRules`. */
const CHARGE_RULE_FIELDS = ["maxOwned", "regenMs", "priceLadder", "currency", "requiresSettledAccount", "premiumRegenDivisor"];

const CHARGE_KINDS = ["standard", "plasma"];

/** Целое число из записи согласия — или `undefined`, если это не оно. */
function wholeInt(value) {
  if (typeof value !== "number" || !Number.isFinite(value)) return undefined;
  const number = Math.floor(value);
  return number >= 0 ? number : undefined;
}

/**
 * Правила заряда из записи согласия: все поля обязаны стоять.
 *
 * Согласие, не называющее поле, сверять нельзя: пропуск поля молчаливо сравнил бы «не названо»
 * с нынешним значением и пропустил бы мутацию, согласие на которую неполно. Клиентский кодек
 * пишет согласие целиком, так что неполная запись — криво собранный конверт, а не старый клиент.
 */
function capturedChargeRules(source) {
  const rules = source && typeof source === "object" && !Array.isArray(source) ? source : {};
  const maxOwned = wholeInt(rules.maxOwned);
  const regenMs = wholeInt(rules.regenMs);
  const premiumRegenDivisor = wholeInt(rules.premiumRegenDivisor);
  if (maxOwned === undefined || regenMs === undefined || premiumRegenDivisor < 1) return null;
  if (!Array.isArray(rules.priceLadder) || rules.priceLadder.length === 0) return null;
  const priceLadder = rules.priceLadder.map((rung) => wholeInt(rung));
  if (priceLadder.some((rung) => rung === undefined)) return null;
  if (typeof rules.currency !== "string" || !rules.currency) return null;
  if (typeof rules.requiresSettledAccount !== "boolean") return null;
  return {maxOwned, regenMs, priceLadder, currency: rules.currency, requiresSettledAccount: rules.requiresSettledAccount, premiumRegenDivisor};
}

/**
 * Читает захваченные платные условия из конверта мутации.
 *
 * Поле `capturedTerms` необязательное: для операции без релевантных платных условий его нет и
 * не должно быть. Но приехавшее поле обязано быть полным согласием: версия, цена каждому виду
 * попыток и правила обоих зарядов. Объект принимается и в форме строки — согласие едет из
 * очереди, которая хранит конверт как есть; трактовка та же, что у `parseMutationPayload`.
 *
 * @returns {{valid: boolean, captured: object|null, reason: string}}
 */
function readCapturedTerms(data) {
  const envelope = data || {};
  const raw = envelope.capturedTerms;
  if (raw === null || raw === undefined) return {valid: true, captured: null, reason: "absent"};

  let source = raw;
  if (typeof raw === "string") {
    if (raw.length > 64 * 1024) return {valid: false, captured: null, reason: "capturedTerms is too long"};
    try {
      source = JSON.parse(raw);
    } catch (error) {
      return {valid: false, captured: null, reason: "capturedTerms is not valid JSON"};
    }
  }
  if (source === null || typeof source !== "object" || Array.isArray(source)) {
    return {valid: false, captured: null, reason: "capturedTerms must be an object"};
  }

  const version = wholeInt(source.version);
  if (version === undefined) return {valid: false, captured: null, reason: "capturedTerms.version must be a non-negative integer"};

  const prices = source.activityPrices && typeof source.activityPrices === "object" && !Array.isArray(source.activityPrices)
    ? source.activityPrices : null;
  if (!prices) return {valid: false, captured: null, reason: "capturedTerms.activityPrices must be an object"};
  const activityPrices = {};
  for (const kind of ACTIVITY_KINDS) {
    const price = wholeInt(prices[kind]);
    if (price === undefined) return {valid: false, captured: null, reason: `capturedTerms.activityPrices.${kind} must be a non-negative integer`};
    activityPrices[kind] = price;
  }

  const rules = {};
  for (const kind of CHARGE_KINDS) {
    const parsed = capturedChargeRules(source[kind]);
    if (!parsed) return {valid: false, captured: null, reason: `capturedTerms.${kind} must be complete charge rules`};
    rules[kind] = parsed;
  }

  return {valid: true, captured: {version, activityPrices, standard: rules.standard, plasma: rules.plasma}, reason: "present"};
}

/**
 * Платная проекция серверной таблицы — то, с чем сверяется согласие.
 *
 * Поля отбираются поимённо: неплатные ручки таблицы в сверке участвовать не должны, и прокрасться
 * в сравнение им неоткуда. На входе — то, что вернул `readEconomyConstants`: таблица уже
 * нормализована, пропусков цен нет (недостающее заполнено начальным, а не нулём).
 */
function payableTermsProjection(constants) {
  const source = constants && typeof constants === "object" ? constants : {};
  const activityPrices = {};
  const prices = source.activityPrices && typeof source.activityPrices === "object" ? source.activityPrices : {};
  for (const kind of ACTIVITY_KINDS) {
    const price = Number(prices[kind]);
    activityPrices[kind] = Number.isFinite(price) ? price : 0;
  }
  const rules = {};
  for (const kind of CHARGE_KINDS) {
    const raw = source[kind] && typeof source[kind] === "object" ? source[kind] : {};
    rules[kind] = {
      maxOwned: Number.isFinite(Number(raw.maxOwned)) ? Number(raw.maxOwned) : 0,
      regenMs: Number.isFinite(Number(raw.regenMs)) ? Number(raw.regenMs) : 0,
      priceLadder: Array.isArray(raw.priceLadder) ? raw.priceLadder.map((rung) => Number(rung)) : [],
      currency: typeof raw.currency === "string" ? raw.currency : "",
      requiresSettledAccount: raw.requiresSettledAccount === true,
      premiumRegenDivisor: Number.isFinite(Number(raw.premiumRegenDivisor)) ? Number(raw.premiumRegenDivisor) : 1,
    };
  }
  return {version: Number(source.version) || 0, activityPrices, standard: rules.standard, plasma: rules.plasma};
}

function chargeRulesEqual(a, b) {
  return CHARGE_RULE_FIELDS.every((field) => {
    const left = a[field];
    const right = b[field];
    if (Array.isArray(left) || Array.isArray(right)) {
      return Array.isArray(left) && Array.isArray(right) &&
        left.length === right.length && left.every((rung, index) => rung === right[index]);
    }
    return left === right;
  });
}

/**
 * Сверка согласия с текущими платными условиями.
 *
 * Решающим является совпадение платных полей — прейскуранта и правил зарядов, поимённо.
 * Номер версии в сравнении не участвует: согласие сверяется по ценам, а не по номеру, иначе
 * правка неплатной ручки (поднимающая версию) заставляла бы переспрашивать согласие по
 * неизменным ценам — в точности как в клиентском `TermsConsentGate`.
 *
 * @param {object|null} captured условия из конверта; `null` — согласия в мутации нет.
 * @param {object} current платная проекция текущей таблицы ({@link payableTermsProjection}).
 * @returns {{sufficient: boolean, change: object|null}}
 */
function termsVerdict(captured, current) {
  if (!captured) return {sufficient: false, change: null};

  const prices = {};
  for (const kind of ACTIVITY_KINDS) {
    const from = captured.activityPrices[kind];
    const to = current.activityPrices[kind];
    if (from !== to) prices[kind] = {from, to};
  }
  const chargeRules = {};
  for (const kind of CHARGE_KINDS) {
    if (!chargeRulesEqual(captured[kind], current[kind])) {
      chargeRules[kind] = {from: captured[kind], to: current[kind]};
    }
  }

  if (Object.keys(prices).length === 0 && Object.keys(chargeRules).length === 0) {
    return {sufficient: true, change: null};
  }
  return {sufficient: false, change: {prices, chargeRules}};
}

module.exports = {
  TERMS_CONSENT_DETAIL,
  payableTermsProjection,
  readCapturedTerms,
  termsVerdict,
};
