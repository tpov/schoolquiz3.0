"use strict";

const {spawnSync} = require("child_process");
const fs = require("fs");

/**
 * `node --check` over every source file here, discovered rather than listed.
 *
 * The `lint` script used to name each file in an `&&` chain, which meant a file was linted only if
 * somebody remembered to add it — and the ones most likely to be forgotten are the ones just
 * written, which are also the ones most likely to have a syntax error in them. Discovery removes
 * the step that gets skipped.
 *
 * Every file is checked and every failure is reported, for the same reason `run-tests.js` runs
 * every suite: one broken file must not hide the next.
 */
function main() {
  const files = fs.readdirSync(__dirname).filter((name) => name.endsWith(".js")).sort();
  const failed = [];
  for (const name of files) {
    const result = spawnSync(process.execPath, ["--check", name], {cwd: __dirname, stdio: "inherit"});
    if (result.error !== undefined || result.signal !== null || result.status !== 0) failed.push(name);
  }
  console.log(`${files.length - failed.length} of ${files.length} files parse`);
  if (failed.length > 0) {
    console.error(`FAILED: ${failed.join(", ")}`);
    process.exitCode = 1;
  }
}

main();
