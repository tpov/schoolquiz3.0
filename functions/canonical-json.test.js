"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {canonicalJson} = require("./canonical-json");

test("canonical JSON is independent of Firestore map insertion order", () => {
  const first = {outer: {z: 1, a: [true, {q: null, p: "x"}]}, b: 2};
  const second = {b: 2, outer: {a: [true, {p: "x", q: null}], z: 1}};
  assert.equal(canonicalJson(first), canonicalJson(second));
});

test("canonical JSON rejects values that cannot be a stable checkpoint", () => {
  for (const value of [{missing: undefined}, {fraction: 1.5}, {when: new Date()}, [, "sparse"]]) {
    assert.throws(() => canonicalJson(value), TypeError);
  }
});
