"use strict";

// Firestore map fields have no insertion-order contract. Checkpoints and their fingerprints must
// therefore hash a canonical JSON representation, never the object order returned by an SDK.
// This intentionally accepts only the exact JSON subset used by server-owned checkpoints.
function canonicalJson(value) {
  if (value === null || typeof value === "string" || typeof value === "boolean") return JSON.stringify(value);
  if (typeof value === "number" && Number.isSafeInteger(value)) return JSON.stringify(value);
  if (Array.isArray(value)) {
    const names = Object.getOwnPropertyNames(value);
    if (Object.getOwnPropertySymbols(value).length !== 0 || names.length !== value.length + 1 || names.at(-1) !== "length") {
      throw new TypeError("Canonical JSON requires dense exact arrays");
    }
    for (let index = 0; index < value.length; index++) {
      if (names[index] !== String(index) || !Object.hasOwn(value, index)) {
        throw new TypeError("Canonical JSON requires dense exact arrays");
      }
    }
    return `[${value.map(canonicalJson).join(",")}]`;
  }
  if (value && Object.getPrototypeOf(value) === Object.prototype) {
    const keys = Object.keys(value);
    if (Object.getOwnPropertySymbols(value).length !== 0 || Object.getOwnPropertyNames(value).length !== keys.length ||
        keys.some((key) => {
          const descriptor = Object.getOwnPropertyDescriptor(value, key);
          return !descriptor || !descriptor.enumerable || !Object.hasOwn(descriptor, "value");
        })) {
      throw new TypeError("Canonical JSON requires exact object fields");
    }
    return `{${keys.sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(",")}}`;
  }
  throw new TypeError("Canonical JSON requires exact JSON values");
}

module.exports = {canonicalJson};
