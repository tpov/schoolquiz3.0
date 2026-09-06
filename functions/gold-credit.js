"use strict";

/** Exact ADM-19 debt-first credit, reusable by any server-authorised gold source. No I/O/prices. */
function planGoldCredit({gold, shortfall, gross}) {
  for (const [name, value] of Object.entries({gold, shortfall, gross})) {
    if (!Number.isSafeInteger(value) || value < 0) throw new TypeError(`${name} must be an exact nonnegative integer`);
  }
  const shortfallCleared = Math.min(shortfall, gross);
  const net = gross - shortfallCleared;
  const goldAfter = gold + net;
  if (!Number.isSafeInteger(goldAfter)) throw new RangeError("Gold credit exceeds exact integer range");
  return {goldBefore: gold, goldAfter, shortfallBefore: shortfall, shortfallAfter: shortfall - shortfallCleared,
    gross, net, shortfallCleared};
}

module.exports = {planGoldCredit};
