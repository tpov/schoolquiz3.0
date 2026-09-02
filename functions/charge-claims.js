"use strict";

/**
 * Заявки на заряды: маска рядом с codeAnswer, расчёт при отправке, проверка перерасхода.
 *
 * Заявка — не транзакция (CAP-3). Клиент записывает, что знает: пропущенный вопрос — это `0`, и
 * только сервер, взяв оплату, поднимает позицию до `9`. Клиент, написавший `9` сам, заявляет
 * ответ, которого не давал, — и маска делает это различимым.
 *
 * Маска — строка той же длины, что `codeAnswer`, по символу на позицию: `.` заявки нет, `S` —
 * обычный заряд (подсказка на лёгком), `P` — плазменный (пропуск сложного). Рядом, а не внутри:
 * `codeAnswer` остаётся цифрами, `recomputePercentScore` и его Kotlin-зеркало не трогаются, а
 * попытка без маски ведёт себя ровно как сегодня.
 *
 * Зеркало `shared/core/scoring/.../ChargeClaimMask.kt`; общий набор фикстур в
 * `config/charge-claim-fixtures.json` держит их вместе. Чистый модуль: без firebase-admin.
 */

const NONE = ".";
const STANDARD = "S";
const PLASMA = "P";

/** Чем маска может быть испорчена. Испорченная маска — искажённый payload, а не перерасход. */
const FAULT_LENGTH_MISMATCH = "LENGTH_MISMATCH";
const FAULT_BAD_CHAR = "BAD_CHAR";
const FAULT_WRONG_DIFFICULTY = "WRONG_DIFFICULTY";
const FAULT_SKIP_ON_ANSWERED = "SKIP_ON_ANSWERED";

/** Куда пишется запись о перерасходе. Закрыть правилами для клиентов целиком: это читает оператор. */
const OVERSPEND_AUDIT_PATH = "audit/charge_overspend/records";

function text(value) {
  return value === null || value === undefined ? "" : String(value);
}

/** Маска без единой заявки — то, что несёт попытка, где зарядов не тратили. */
function noClaims(length) {
  return NONE.repeat(Math.max(0, Math.floor(Number(length) || 0)));
}

/**
 * Проверяет маску против попытки. Возвращает код ошибки или `null`.
 *
 * @param difficulty сложность попытки целиком: подсказки живут только на лёгких, пропуски — только
 *   на сложных, и у попытки одна сложность на все вопросы (CAP-1).
 */
function validateClaimMask(mask, codeAnswer, difficulty) {
  const claims = text(mask);
  const digits = text(codeAnswer);
  if (claims.length !== digits.length) return FAULT_LENGTH_MISMATCH;
  if (!/^[.SP]*$/.test(claims)) return FAULT_BAD_CHAR;
  const allowed = text(difficulty).toUpperCase() === "HARD" ? PLASMA : STANDARD;
  for (let index = 0; index < claims.length; index += 1) {
    const claim = claims[index];
    if (claim !== NONE && claim !== allowed) return FAULT_WRONG_DIFFICULTY;
    // Пропущенный вопрос не отвечают — у него `0`. Ненулевая цифра под `P` значит, что клиент
    // оценил себя сам и просит оплатить уже поставленное.
    if (claim === PLASMA && digits[index] !== "0") return FAULT_SKIP_ON_ANSWERED;
  }
  return null;
}

/** Сколько заявок каждого вида несёт маска. */
function countClaims(mask) {
  const claims = text(mask);
  let standard = 0;
  let plasma = 0;
  for (const claim of claims) {
    if (claim === STANDARD) standard += 1;
    else if (claim === PLASMA) plasma += 1;
  }
  return {standard, plasma};
}

/**
 * Порядок, в котором вопросы задавались, — по времени ответа.
 *
 * Частичная оплата платит за самые ранние заявки, а не за произвольное подмножество, и «раннее»
 * значит «спрошенное раньше», а не «стоящее левее в строке»: раннер тасует набор, и позиция в
 * строке порядку показа не соответствует. Ответов может не быть вовсе — тогда порядок строки и
 * есть единственный известный, и `settleClaims` берёт его сам.
 *
 * @param answers строки ответов попытки: `codeAnswerIndex` и `answeredAtMs`
 */
function askedOrder(answers) {
  return (Array.isArray(answers) ? answers : [])
    .slice()
    .sort((a, b) => (Number(a && a.answeredAtMs) || 0) - (Number(b && b.answeredAtMs) || 0))
    .map((answer) => Math.floor(Number(answer && answer.codeAnswerIndex) || 0));
}

/**
 * Списывает заявки в пределах того, что есть, в порядке `order`.
 *
 * Порядок обязателен: частичная оплата платит за самые ранние заявки, а не за произвольное
 * подмножество. По умолчанию — порядок позиций в строке; вызывающий, у которого есть время каждого
 * ответа, передаёт порядок, в котором вопросы задавались.
 *
 * Подсказка и пропуск стоят по целому заряду, не по очкам: они неделимы. Подсказка ничего не
 * поднимает — она лишь показала ответ, а цифра — то, что игрок после этого ответил. Только
 * оплаченный пропуск засчитывается верным; неоплаченный оставляет `0`: вопрос засчитан как
 * неотвеченный, чем он и был.
 *
 * @param standardAvailable сколько целых обычных зарядов есть
 * @param plasmaAvailable сколько целых плазменных
 */
function settleClaims(mask, codeAnswer, standardAvailable, plasmaAvailable, order) {
  const claims = text(mask);
  const digits = text(codeAnswer).split("");
  if (claims.length !== digits.length) {
    throw new Error("mask and codeAnswer must be the same length");
  }
  const standardStart = Math.max(0, Math.floor(Number(standardAvailable) || 0));
  const plasmaStart = Math.max(0, Math.floor(Number(plasmaAvailable) || 0));
  let standard = standardStart;
  let plasma = plasmaStart;
  const paid = [];
  const unpaid = [];
  const seen = new Set();
  const positions = (Array.isArray(order) ? order : []).concat(claims.split("").map((_, i) => i));
  for (const raw of positions) {
    const index = Math.floor(Number(raw));
    if (!(index >= 0 && index < claims.length) || seen.has(index)) continue;
    seen.add(index);
    const claim = claims[index];
    if (claim === STANDARD) {
      if (standard > 0) {
        standard -= 1;
        paid.push(index);
      } else {
        unpaid.push(index);
      }
    } else if (claim === PLASMA) {
      if (plasma > 0) {
        plasma -= 1;
        paid.push(index);
        digits[index] = "9";
      } else {
        unpaid.push(index);
      }
    }
  }
  return {
    codeAnswer: digits.join(""),
    paid: paid.sort((a, b) => a - b),
    unpaid: unpaid.sort((a, b) => a - b),
    standardChargesPaid: standardStart - standard,
    plasmaChargesPaid: plasmaStart - plasma,
  };
}

/**
 * Проверка перерасхода одного вида заряда над окном, которое покрывает пакет попыток.
 *
 * Потолок — максимум, который аккаунт мог держать к концу окна: что было в базе плюс сколько
 * успело восстановиться, но не выше бака. Считается в **очках**, а в заряды переводится один раз
 * в самом конце: округлять порознь то, что лежало, и то, что натекло, значило бы терять по
 * неполному заряду с каждой стороны — и честная заявка, которую баланс покрывает, попадала бы в
 * запись как перерасход.
 *
 * Бак — купленные слоты, не выше `maxOwned`; баланс выше бака не отбирается и считается тем, что
 * аккаунт действительно держал (понижение потолка не конфискует). Сверх бака — запас на
 * расхождение часов, тоже в очках восстановления. Всё, что заявлено выше, — излишек. Он и так не
 * оплачен (см. `settleClaims`): запись не наказывает второй раз, она для оператора.
 *
 * Медленная синхронизация — не подделка: неделя офлайн даёт длинное окно, и потолок растёт с ним.
 *
 * @param storedPoints очки в базе на момент `storedUpdatedAtMs`
 * @param rules правила вида: `maxOwned`, `regenMs`
 * @param ownedSlots сколько слотов у аккаунта куплено; по умолчанию — весь `maxOwned`
 * @param pointsPerCharge очков в заряде
 */
function overspendVerdict({
  claimed, storedPoints, storedUpdatedAtMs, windowEndMs, rules, ownedSlots, clockSkewToleranceMs,
  pointsPerCharge,
}) {
  const perCharge = Math.max(1, Math.floor(Number(pointsPerCharge) || 100));
  const maxOwned = Math.max(0, Math.floor(Number(rules && rules.maxOwned) || 0));
  const slots = ownedSlots === undefined || ownedSlots === null ?
    maxOwned : Math.max(0, Math.floor(Number(ownedSlots) || 0));
  const regenMs = Math.max(0, Math.floor(Number(rules && rules.regenMs) || 0));
  const heldPoints = Math.max(0, Math.floor(Number(storedPoints) || 0));
  const elapsedMs = Math.max(0, Math.floor(Number(windowEndMs) || 0) - Math.floor(Number(storedUpdatedAtMs) || 0));
  const regeneratedPoints = regenMs > 0 ? Math.floor((elapsedMs * perCharge) / regenMs) : 0;
  const tankPoints = Math.min(slots, maxOwned) * perCharge;
  const ceilingPoints = Math.max(heldPoints, Math.min(tankPoints, heldPoints + regeneratedPoints));
  const skewPoints = regenMs > 0 ?
    Math.floor((Math.max(0, Number(clockSkewToleranceMs) || 0) * perCharge) / regenMs) : 0;
  const claims = Math.max(0, Math.floor(Number(claimed) || 0));
  const ceiling = Math.floor(ceilingPoints / perCharge);
  const allowance = Math.floor((ceilingPoints + skewPoints) / perCharge);
  return {
    claimed: claims,
    held: Math.floor(heldPoints / perCharge),
    heldPoints,
    regenerated: Math.floor(regeneratedPoints / perCharge),
    regeneratedPoints,
    ceiling,
    skewAllowance: allowance - ceiling,
    allowance,
    surplus: Math.max(0, claims - allowance),
  };
}

/**
 * Запись о перерасходе — обоснование внутри, а не выводимое из неё.
 *
 * Восстанавливает вывод без перечитывания попыток: аккаунт, окно, что лежало в базе и когда,
 * версия таблицы, посчитанный потолок, заявлено, излишек, идентификаторы попыток, которые его
 * принесли. «Обоснование» значит, что причина в записи, а не выводится из неё.
 */
function overspendRecord({
  uid, chargeKind, windowStartMs, windowEndMs, storedPoints, storedUpdatedAtMs,
  constantsVersion, verdict, attemptIds, recordedAtMs,
}) {
  const kind = text(chargeKind).toUpperCase();
  const ids = (Array.isArray(attemptIds) ? attemptIds : []).map(text).filter(Boolean);
  return {
    id: `${text(uid)}_${kind}_${Math.floor(Number(windowEndMs) || 0)}`,
    uid: text(uid),
    chargeKind: kind,
    windowStartMs: Math.floor(Number(windowStartMs) || 0),
    windowEndMs: Math.floor(Number(windowEndMs) || 0),
    storedPoints: Math.floor(Number(storedPoints) || 0),
    storedUpdatedAtMs: Math.floor(Number(storedUpdatedAtMs) || 0),
    constantsVersion: Math.floor(Number(constantsVersion) || 0),
    held: verdict.held,
    regenerated: verdict.regenerated,
    ceiling: verdict.ceiling,
    skewAllowance: verdict.skewAllowance,
    allowance: verdict.allowance,
    claimed: verdict.claimed,
    surplus: verdict.surplus,
    attemptIds: ids,
    reason:
      `Заявлено ${verdict.claimed} ${kind}, а к концу окна аккаунт мог держать не больше ` +
      `${verdict.ceiling} (в базе ${verdict.held}, восстановилось ${verdict.regenerated}, ` +
      `допуск на часы ${verdict.skewAllowance}); излишек ${verdict.surplus} не оплачен.`,
    recordedAtMs: Math.floor(Number(recordedAtMs) || 0),
  };
}

module.exports = {
  NONE,
  STANDARD,
  PLASMA,
  FAULT_LENGTH_MISMATCH,
  FAULT_BAD_CHAR,
  FAULT_WRONG_DIFFICULTY,
  FAULT_SKIP_ON_ANSWERED,
  OVERSPEND_AUDIT_PATH,
  askedOrder,
  countClaims,
  noClaims,
  overspendRecord,
  overspendVerdict,
  settleClaims,
  validateClaimMask,
};
