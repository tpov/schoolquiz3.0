"use strict";

const assert = require("assert");
const {logoImageUrl, isLogoName, logoImageBytes} = require("./logos.js");

// The whole pool is addressable and named the same everywhere.
assert.ok(isLogoName("Golden Crown Logo"), "the pool answers a known avatar");
assert.ok(!isLogoName("Not A Logo"), "unknown names are refused");

// URLs are deterministic and URL-safe; the image endpoint can find what it was asked for.
const url = logoImageUrl("Golden Crown Logo");
assert.ok(url.startsWith("https://"), "url is absolute");
assert.ok(url.includes("logoImage"), "url points at the image function");
assert.ok(logoImageBytes("Golden Crown Logo").length > 0, "every known logo has bytes");
assert.strictEqual(logoImageBytes("Not A Logo"), null, "unknown logos have no bytes");

// The pool never changes shape silently: adding an avatar is a code change everywhere at once.
const {LOGO_IMAGES} = require("./logo-images.js");
for (const name of Object.keys(LOGO_IMAGES)) {
  assert.ok(isLogoName(name), `every embedded image is a known logo: ${name}`);
  assert.ok(logoImageBytes(name).length > 0, `every embedded image has bytes: ${name}`);
}
