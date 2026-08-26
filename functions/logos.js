"use strict";

/**
 * The NOIR avatar pool: eight images, served by the logoImage HTTP function.
 *
 * The avatars are game items — boxes drop them, the market trades them — and their images live
 * here with the code rather than in Storage: the pool is closed, tiny (≈10 KB), and versioned with
 * the functions, so the catalogue, the image endpoint and the URLs can never disagree.
 */
const {LOGO_IMAGES} = require("./logo-images.js");

const REGION = "us-central1";

/** Public URL of the image for a logo. Deterministic, so it can be stored and cached. */
function logoImageUrl(logoName) {
  const project = process.env.GCLOUD_PROJECT || "schoolquiz";
  return `https://${REGION}-${project}.cloudfunctions.net/logoImage?logo=${encodeURIComponent(logoName)}`;
}

/** True when the name is one of the pool's avatars. */
function isLogoName(value) {
  return typeof value === "string" && Object.prototype.hasOwnProperty.call(LOGO_IMAGES, value);
}

function logoImageBytes(logoName) {
  return LOGO_IMAGES[logoName] || null;
}

module.exports = {logoImageUrl, isLogoName, logoImageBytes};
