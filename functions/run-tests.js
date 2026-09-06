"use strict";

const {spawnSync} = require("child_process");
const fs = require("fs");
const path = require("path");

/**
 * Runs every suite in this directory and reports all of them.
 *
 * The `test` script used to be one `&&` chain of thirty-odd `node x.test.js` calls, which meant the
 * first red suite ended the run. That is not a small inconvenience: `entity-version.test.js` sat at
 * position thirteen of thirty-four, red for a reason owned elsewhere, and the twenty-one suites
 * behind it — every module of the scoring chain among them — had not been executed by `npm test`
 * for as long as it stayed red. A suite nobody runs is a suite that is already broken and has not
 * been told yet.
 *
 * So: every file is run, every result is printed, and the exit status is the *aggregate*. One red
 * suite is one red line, not a wall in front of the others.
 *
 * Discovered from the directory rather than listed, so a new suite is picked up by existing.
 * A listed set is a set someone forgets to add to; the whole point of the previous chain's
 * `&&`-list was that it had to be edited by hand, and the last file added to it was added by hand.
 *
 * Each suite runs in its own process, because they are written as scripts with a top-level runner
 * and `process.exitCode`, and because a suite that crashes the interpreter must not take the run
 * with it — that is the failure mode this file exists to stop.
 */

const SUITE_PATTERN = /\.test\.js$/;

function suiteFiles(directory) {
  return fs
    .readdirSync(directory)
    .filter((name) => SUITE_PATTERN.test(name))
    .sort();
}

function runSuite(directory, name) {
  const started = Date.now();
  const result = spawnSync(process.execPath, [name], {
    cwd: directory,
    encoding: "utf8",
    // Inherited, so a suite's own output is not swallowed and buffered into a summary nobody reads.
    stdio: "inherit",
  });
  return {
    name,
    ms: Date.now() - started,
    // A signal — a crash, an out-of-memory kill — is a failure with no exit code at all, so a bare
    // `status !== 0` check would read `null` as a pass.
    ok: result.error === undefined && result.signal === null && result.status === 0,
    detail: result.error ? String(result.error.message) : (result.signal || `exit ${result.status}`),
  };
}

function main() {
  const directory = __dirname;
  const files = suiteFiles(directory);
  if (files.length === 0) {
    console.error("run-tests: no *.test.js files found — that is a broken checkout, not a pass");
    process.exitCode = 1;
    return;
  }

  const results = files.map((name) => runSuite(directory, name));
  const failed = results.filter((result) => !result.ok);

  console.log("");
  console.log("──────────────────────────────────────────────────────────────");
  for (const result of results) {
    const status = result.ok ? "PASS" : "FAIL";
    const why = result.ok ? "" : `  (${result.detail})`;
    console.log(`${status}  ${result.name}${why}`);
  }
  console.log("──────────────────────────────────────────────────────────────");
  console.log(`${results.length - failed.length} of ${results.length} suites passed`);

  if (failed.length > 0) {
    console.error(`FAILED: ${failed.map((result) => result.name).join(", ")}`);
    process.exitCode = 1;
  }
}

main();
