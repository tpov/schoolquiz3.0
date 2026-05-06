#!/usr/bin/env node
"use strict";

const readline = require("readline");
const path = require("path");
const {spawn, spawnSync} = require("child_process");

const E2E_ROOT = path.resolve(__dirname, "..");
const REPO_ROOT = path.resolve(E2E_ROOT, "../..");
const DEFAULT_ROLES = ["developer", "participant", "tester", "moderator", "admin", "full_access"];
const APPIUM_BASE_PORT = Number(process.env.APPIUM_BASE_PORT || 4723);
const SYSTEM_BASE_PORT = Number(process.env.APPIUM_SYSTEM_BASE_PORT || 8200);
const PREFIX = safeId(process.env.APPIUM_E2E_PREFIX || `appium_multi_${Date.now()}`);
const INCLUDE_EMULATORS = envBool("INCLUDE_EMULATORS", false);
const SKIP_BUILD = envBool("SKIP_BUILD", false);

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});

async function main() {
  const devices = selectedDevices();
  if (devices.length === 0) {
    throw new Error("No Android devices found. Check: adb devices -l");
  }

  const roles = splitEnv("APPIUM_E2E_PROFILE_ROLES", DEFAULT_ROLES);
  const maxParallel = positiveNumber(process.env.MAX_PARALLEL) || devices.length;

  console.log(`Appium multi-device prefix: ${PREFIX}`);
  console.log(`Devices: ${devices.length}`);
  for (const [index, device] of devices.entries()) {
    console.log(
      `- ${index + 1}. ${device.serial} ${device.model || ""} role=${roles[index % roles.length]}`,
    );
  }
  console.log(`Max parallel: ${maxParallel}`);

  if (!SKIP_BUILD) {
    run("./gradlew", [":apps:android-next:assembleDebug", "--no-configuration-cache", "--max-workers=2"]);
  }

  const results = [];
  let nextIndex = 0;
  const workers = Array.from({length: Math.min(maxParallel, devices.length)}, async () => {
    while (nextIndex < devices.length) {
      const index = nextIndex++;
      results[index] = await runDevice(devices[index], index, roles[index % roles.length]);
    }
  });
  await Promise.all(workers);

  console.log("\nMulti-device summary:");
  for (const result of results) {
    const status = result.code === 0 ? "OK" : `FAIL(${result.code})`;
    console.log(`- ${status} ${result.serial} ${result.model || ""} role=${result.role}`);
  }

  const failed = results.filter((result) => result.code !== 0);
  if (failed.length > 0) {
    throw new Error(`${failed.length} device flow(s) failed`);
  }
}

function selectedDevices() {
  const requested = splitEnv("ANDROID_SERIALS", []);
  const all = listDevices().filter((device) => INCLUDE_EMULATORS || !device.serial.startsWith("emulator-"));
  if (requested.length === 0) return all;
  const bySerial = new Map(all.map((device) => [device.serial, device]));
  return requested.map((serial) => {
    const found = bySerial.get(serial);
    if (!found) throw new Error(`Requested device is not connected: ${serial}`);
    return found;
  });
}

function listDevices() {
  const result = spawnSync("adb", ["devices", "-l"], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stderr || "adb devices failed");
  return result.stdout
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("List of devices"))
    .map(parseDeviceLine)
    .filter(Boolean);
}

function parseDeviceLine(line) {
  const parts = line.split(/\s+/);
  if (parts.length < 2 || parts[1] !== "device") return null;
  const fields = {};
  for (const part of parts.slice(2)) {
    const separator = part.indexOf(":");
    if (separator > 0) {
      fields[part.slice(0, separator)] = part.slice(separator + 1);
    }
  }
  return {
    serial: parts[0],
    model: fields.model,
    product: fields.product,
    device: fields.device,
  };
}

function runDevice(device, index, role) {
  const appiumPort = APPIUM_BASE_PORT + index;
  const systemPort = SYSTEM_BASE_PORT + index;
  const devicePrefix = `${PREFIX}_${index + 1}_${safeId(device.model || device.serial)}`;
  const label = `${index + 1}:${device.model || device.serial}`;
  const env = {
    ...process.env,
    ANDROID_SERIAL: device.serial,
    APPIUM_PORT: String(appiumPort),
    APPIUM_SYSTEM_PORT: String(systemPort),
    APPIUM_E2E_PREFIX: devicePrefix,
    APPIUM_E2E_PROFILE_ROLE: role,
    SKIP_BUILD: "1",
  };

  console.log(
    `\n[${label}] start role=${role} appiumPort=${appiumPort} systemPort=${systemPort} prefix=${devicePrefix}`,
  );
  const child = spawn(process.execPath, ["./src/arena-rating-sync.e2e.js"], {
    cwd: E2E_ROOT,
    env,
    stdio: ["ignore", "pipe", "pipe"],
  });
  pipeWithPrefix(child.stdout, label);
  pipeWithPrefix(child.stderr, label);
  return new Promise((resolve) => {
    child.on("close", (code) => {
      resolve({
        serial: device.serial,
        model: device.model,
        role,
        code: code == null ? 1 : code,
      });
    });
  });
}

function pipeWithPrefix(stream, label) {
  const rl = readline.createInterface({input: stream});
  rl.on("line", (line) => {
    console.log(`[${label}] ${line}`);
  });
}

function run(command, args) {
  console.log(`\n$ ${command} ${args.join(" ")}`);
  const result = spawnSync(command, args, {
    cwd: REPO_ROOT,
    stdio: "inherit",
    env: process.env,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`${command} exited with code ${result.status}`);
  }
}

function splitEnv(name, fallback) {
  const value = process.env[name];
  if (value == null || value.trim() === "") return fallback;
  return value.split(",").map((part) => part.trim()).filter(Boolean);
}

function positiveNumber(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : null;
}

function envBool(name, defaultValue) {
  const value = process.env[name];
  if (value == null || value === "") return defaultValue;
  return /^(1|true|yes|y)$/i.test(value);
}

function safeId(value) {
  return String(value).replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 80);
}
