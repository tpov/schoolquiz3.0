#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const {spawnSync} = require("child_process");

const E2E_ROOT = path.resolve(__dirname, "..");
const REPO_ROOT = path.resolve(E2E_ROOT, "../..");
const DEFAULT_SERVICE_ACCOUNT =
  "/home/tpov/Downloads/school-quiz-89336951-firebase-adminsdk-h5hhr-0d54a7e117.json";
const serviceAccountPath = process.env.SCHOOLQUIZ_FIREBASE_SERVICE_ACCOUNT || DEFAULT_SERVICE_ACCOUNT;

main();

function main() {
  checkNode();
  checkLocalBinary("appium");
  run("adb", ["devices", "-l"], {cwd: REPO_ROOT});
  run(localBin("appium"), ["driver", "list", "--installed"], {cwd: E2E_ROOT});

  if (fs.existsSync(serviceAccountPath)) {
    console.log(`OK service account: ${serviceAccountPath}`);
  } else {
    console.log(`WARN service account not found: ${serviceAccountPath}`);
  }

  console.log("OK Appium E2E doctor finished");
}

function checkNode() {
  const major = Number(process.versions.node.split(".")[0]);
  if (major < 20) {
    throw new Error(`Node >= 20 is required, got ${process.version}`);
  }
  console.log(`OK node ${process.version}`);
}

function checkLocalBinary(name) {
  const binary = localBin(name);
  if (!fs.existsSync(binary)) {
    throw new Error(`Missing ${binary}. Run: cd e2e/appium && npm install`);
  }
  console.log(`OK ${binary}`);
}

function localBin(name) {
  return path.join(E2E_ROOT, "node_modules", ".bin", name);
}

function run(command, args, options = {}) {
  console.log(`\n$ ${[command].concat(args).join(" ")}`);
  const result = spawnSync(command, args, {
    cwd: options.cwd || REPO_ROOT,
    stdio: "inherit",
    env: process.env,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`${command} exited with code ${result.status}`);
  }
}
