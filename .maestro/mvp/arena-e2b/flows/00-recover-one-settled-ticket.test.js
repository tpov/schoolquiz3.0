"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const flow = fs.readFileSync(path.join(__dirname, "00-recover-one-settled-ticket.yaml"), "utf8");
const executable = flow.split("\n").filter((line) => !line.trimStart().startsWith("#")).join("\n");
function position(text) {
  const index = executable.indexOf(text);
  assert.notEqual(index, -1, "missing flow fragment: " + text);
  return index;
}

assert.equal(executable.includes("E2B_SELECTOR_LOAD_OR_REFRESH_LESSONS"), false);
assert.equal(executable.includes("E2B_SELECTOR_CATALOG_LOADED"), false);
assert.equal(executable.includes("E2B_SELECTOR_REFRESH_BALANCE"), false);
assert.doesNotMatch(executable, /PREPARE|RESTORE_SETTLEMENT|RATE_LESSON/);
assert.doesNotMatch(executable, /E2B_SELECTOR_(?:TICKET_START|TICKET_OPEN|SETTLE_|RATING_|RATE_|SYNCHRONIZE)/);

const localTickets = position("E2B_SELECTOR_SHOW_LOCAL_TICKETS");
const target = position("E2B_SELECTOR_TARGET_TICKET_LABEL");
const recover = position("E2B_SELECTOR_RECOVER_TICKET");
const terminal = position("E2B_SELECTOR_TERMINAL_TICKET");
assert.ok(localTickets < target && target < recover && recover < terminal, "recovery order must remain local-list → exact ticket → recover → terminal proof");
assert.match(flow, /below:\n\s+text: \$\{E2B_SELECTOR_TARGET_TICKET_LABEL\}/);
assert.match(flow, /- scroll\n- assertVisible: \$\{E2B_SELECTOR_TARGET_TICKET_LABEL\}/);

console.log("recover-one-settled-ticket flow contract: PASS");
