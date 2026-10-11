"use strict";

/**
 * Покупка премиума за золото — второй путь того же товара (история 2.2).
 *
 * Таблица и здесь единственный источник цены: предложение берётся из `premiumProducts` в момент
 * покупки, поэтому поменять цену — решение на сервере, без выпуска приложения. Списание золота и
 * выдача срока идут одной транзакцией на том же движке `premium-economy`, что и все пути премиума,
 * с именованным ключом идемпотентности; повтор расчёта тот же ключ не начисляет второй раз.
 *
 * Модуль свободен от firebase-admin, как и весь денежный путь: db/transaction и читатели баланса
 * приходят снаружи, решения чистые и покрыты тестами на подделках.
 */

const {SOURCE} = require("./premium-grants");
const {premiumSaleOffer} = require("./economy-constants");
const {LEDGER_PATH, ledgerEntries, REASON} = require("./currency-ledger");
const {readPremiumEconomy, planPremiumEconomy} = require("./premium-economy");

function fail(code, message) {
  const error = new Error(message);
  error.code = code;
  throw error;
}

/**
 * Единственное золотое предложение таблицы, или `null`.
 *
 * Ровно одно: ноль — премиум золотом не продаётся, два и больше — оператор не определился, что
 * продаётся, и продавать первое из списка значило бы прятать выбор от того, кто его сделал. Конфликт
 * с goldPacks отсекает сам `premiumSaleOffer` — такая строка не продаётся ни за что.
 */
function premiumGoldSaleOffer(constants) {
  const products = (constants && constants.premiumProducts) || {};
  const offers =
    Object.keys(products)
      .sort()
      .map((sku) => premiumSaleOffer(constants, sku, "GOLD"))
      .filter((offer) => offer !== null);
  return offers.length === 1 ? offers[0] : null;
}

/**
 * Списывает золото и выдаёт срок одной транзакцией.
 *
 * Догоняет историю премиума до «сейчас» (checkpoint-возврат заставит `runEconomyTransaction`
 * повторить транзакцию), проверяет золото уже по догнавшему балансу и только затем выдаёт лот:
 * планировать выдачу до проверки значило бы дарить срок бедному. Ключ выдачи выводится из
 * аккаунта и момента — переигранная транзакция узнаёт свой лот и не заводит второй.
 *
 * @returns {{caughtUp: boolean, plan?, balance?, offer?}} при `caughtUp: false` план — чекпоинт
 *   догоняющей истории, транзакция обязана завершиться его записью.
 */
async function purchasePremiumWithGold({db, transaction, uid, storedUser, economy, now, readBalance}) {
  const offer = premiumGoldSaleOffer(economy);
  if (!offer) fail("INVALID_ARGUMENT", "Premium is not sold for gold");
  const sourceId = `premium_gold_${uid}_${now}`;
  const operation = {kind: "GRANT", source: SOURCE.GOLD, sourceId, days: offer.days};
  const premiumSnapshot = await readPremiumEconomy({db, transaction, uid, operation});
  let plan =
    planPremiumEconomy({snapshot: premiumSnapshot, userData: storedUser, constants: economy, now, operation: {kind: "ADVANCE"}});
  if (!plan.caughtUp) return {caughtUp: false, plan};
  const before = readBalance({...storedUser, ...plan.userPatch});
  if (before.gold < offer.goldPrice) fail("FAILED_PRECONDITION", "Not enough gold");
  plan =
    planPremiumEconomy({snapshot: premiumSnapshot, userData: storedUser, constants: economy, now, operation});
  if (!plan.caughtUp || plan.outcome !== "GRANTED") {
    fail("INTERNAL", "Premium grant disagrees with its purchase");
  }
  const balance = {...readBalance({...storedUser, ...plan.userPatch}), gold: before.gold - offer.goldPrice};
  for (const entry of ledgerEntries({
    uid,
    reason: REASON.PREMIUM_GOLD_PURCHASE,
    sourceId,
    atMs: now,
    deltas: {gold: -offer.goldPrice},
    details: {sku: offer.sku, days: offer.days},
  })) {
    transaction.set(db.doc(`${LEDGER_PATH}/${entry.id}`), entry, {merge: true});
  }
  return {caughtUp: true, balance, offer, plan};
}

module.exports = {
  premiumGoldSaleOffer,
  purchasePremiumWithGold,
};
