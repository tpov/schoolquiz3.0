"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");

const {redact, STATUS, REDACTED_TYPE, CONTENT_TYPE} = require("./question-redaction");
// The character count is a contract with the client, not with the redactor: the reward this
// module's output is eventually paid from is priced off questionCharsCount, and Kotlin's
// QuestionDisplay.charsCount is a second implementation of it. The fixture pins the two together.
const {questionCharsCount} = require("./lesson-reward");
// The fixtures name concrete shuffles by seed, so this generator must be the one
// question-redaction.test.js draws from too. See _seeded-random.js.
const {seeded} = require("./_seeded-random");

/**
 * The JavaScript half of the harness pinning `question-redaction.js` to Kotlin's
 * `RedactedQuestionContent`.
 *
 * `question-redaction.test.js` proves this module behaves; this file proves it still emits the
 * exact bytes a second implementation, in another language, was written against. Those are
 * different properties: a rename from `items` to `rows` keeps every behavioural test green and
 * breaks the client silently, because the field it wanted simply arrives as undefined.
 *
 * The fixture lives on the Kotlin side, for the same reason the scoring fixture does
 * (`assessment-scoring.test.js`): `shared/core/question-schema/src/jvmTest/resources/` is the only
 * location Gradle already tracks as a test input and that Kotlin can load off the classpath with
 * no working-directory assumption. So this suite takes the relative path — stable because both
 * ends are inside this one repository.
 *
 * A case added to that JSON is picked up by both suites with no edit here or in the Kotlin test.
 * A missing, unreadable or malformed file is a failure reported like any other case, never a skip
 * and never a bare stack trace at require time.
 *
 * Read-only with respect to the module under test: nothing here modifies question-redaction.js.
 */
const FIXTURE_PATH = path.join(
  __dirname,
  "../shared/core/question-schema/src/jvmTest/resources/redacted-question-fixtures.json",
);

const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

/**
 * Loaded on first use from inside a test, not at module scope.
 *
 * At module scope a missing file or a trailing comma aborts the whole run with a bare
 * AssertionError or SyntaxError, no "N of M cases failed" line and — for JSON.parse — no mention of
 * which file was being read. That contradicts this suite's own contract, so every failure mode
 * here is turned into an ordinary assertion with the path in it.
 */
let cached = null;
function fixtures() {
  if (cached) return cached;
  assert.ok(
    fs.existsSync(FIXTURE_PATH),
    `Redacted question fixtures missing at ${FIXTURE_PATH}. This emitter and Kotlin's ` +
    "RedactedQuestionContent are only pinned together by that file; without it there is nothing " +
    "to compare.",
  );
  let text;
  try {
    text = fs.readFileSync(FIXTURE_PATH, "utf8");
  } catch (error) {
    assert.fail(`Could not read ${FIXTURE_PATH}: ${error.message}`);
  }
  try {
    cached = JSON.parse(text);
  } catch (error) {
    assert.fail(`${FIXTURE_PATH} is not valid JSON: ${error.message}`);
  }
  return cached;
}

/** An array that is absent or empty means the harness would pass by testing nothing. */
function requireCases(key) {
  const cases = fixtures()[key];
  assert.ok(Array.isArray(cases), `Fixture file has no "${key}" array`);
  assert.ok(cases.length > 0, `Fixture array "${key}" is empty — nothing would be compared`);
  return cases;
}

/**
 * Runs every case before reporting, rather than throwing on the first mismatch.
 *
 * A renamed field drifts whole families of cases at once, and the useful signal is which families.
 * A case that throws is recorded and the run continues, so one malformed fixture cannot mask the
 * rest of the file.
 */
function checkEveryCase(label, cases, run) {
  const failures = [];
  for (const testCase of cases) {
    const name = testCase.name || "<unnamed case>";
    try {
      const problem = run(testCase);
      if (problem) failures.push(`  "${name}": ${problem}`);
    } catch (error) {
      failures.push(`  "${name}": threw — ${error.message}`);
    }
  }
  assert.ok(
    failures.length === 0,
    `${label}: ${failures.length} of ${cases.length} cases failed against ${FIXTURE_PATH}\n` +
    failures.join("\n"),
  );
}

/**
 * Every *key* named in `forbidden` that appears anywhere in the value's object tree.
 *
 * Keys, walked recursively — deliberately not a substring scan of the payload text. `order` and
 * `info` are ordinary English words, so `"…in order"` inside a question's own prose would trip a
 * text scan, and a scan cannot tell a key from a value in the first place.
 */
function forbiddenKeysIn(value, forbidden) {
  if (Array.isArray(value)) {
    return value.flatMap((item) => forbiddenKeysIn(item, forbidden));
  }
  if (value && typeof value === "object") {
    const here = Object.keys(value).filter((key) => forbidden.includes(key));
    const below = Object.keys(value).flatMap((key) => forbiddenKeysIn(value[key], forbidden));
    return here.concat(below);
  }
  return [];
}

/** Re-runs the emitter over a case's recorded inputs. */
function emit(testCase) {
  return redact(JSON.stringify(testCase.source), testCase.documentDifficulty, {
    random: seeded(testCase.seed),
    questionId: testCase.questionId,
  });
}

// --------------------------------------------------------------------------------------------
// The pin
// --------------------------------------------------------------------------------------------

test("every fixture case is still exactly what redact emits", () => {
  checkEveryCase("emitted payload", requireCases("redacted"), (testCase) => {
    const outcome = emit(testCase);
    if (outcome.status !== STATUS.REDACTED) {
      return `expected status "${STATUS.REDACTED}", got "${outcome.status}" (${outcome.reason})`;
    }
    if (outcome.publicPayload !== testCase.publicPayload) {
      return "the emitted public half no longer matches the fixture.\n" +
        `    fixture: ${testCase.publicPayload}\n` +
        `    emitter: ${outcome.publicPayload}`;
    }
    return null;
  });
});

test("every fixture case names the discriminator its own payload carries", () => {
  checkEveryCase("expectedType", requireCases("redacted"), (testCase) => {
    const known = Object.values(REDACTED_TYPE);
    if (!known.includes(testCase.expectedType)) {
      return `"${testCase.expectedType}" is not one of this module's REDACTED_TYPE values`;
    }
    const actual = JSON.parse(testCase.publicPayload).type;
    if (actual !== testCase.expectedType) {
      return `expectedType is "${testCase.expectedType}" but the payload says "${actual}"`;
    }
    return null;
  });
});

test("every redacted shape this module can emit has a fixture case", () => {
  const covered = new Set(requireCases("redacted").map((c) => c.expectedType));
  const missing = Object.values(REDACTED_TYPE).filter((type) => !covered.has(type));
  assert.deepStrictEqual(
    missing,
    [],
    `REDACTED_TYPE values with no fixture case: ${missing.join(", ")}. A shape nobody pinned is ` +
    "a shape Kotlin is free to get wrong.",
  );
});

test("the answer never appears in a public half", () => {
  const forbidden = fixtures().forbiddenKeys;
  assert.ok(
    Array.isArray(forbidden) && forbidden.length > 0,
    'Fixture file has no "forbiddenKeys" array — the leak check would pass by checking nothing',
  );
  checkEveryCase("answer leak", requireCases("redacted"), (testCase) => {
    const leaked = forbiddenKeysIn(JSON.parse(testCase.publicPayload), forbidden);
    return leaked.length > 0 ? `public half carries the key(s) ${leaked.join(", ")}` : null;
  });
});

test("feeding a public half back in is recognised as already redacted", () => {
  // The only thing that catches REDACTED_TYPE and SOURCE_TYPE_OF drifting apart. If they do, a
  // republish would try to redact an already-redacted half a second time — and since its answer is
  // gone, that is a refusal, which blocks publication of a question that was in fact fine.
  checkEveryCase("already-redacted", requireCases("redacted"), (testCase) => {
    const outcome = redact(testCase.publicPayload, "EASY", {random: seeded(1)});
    if (outcome.status !== STATUS.ALREADY_REDACTED) {
      return `expected "${STATUS.ALREADY_REDACTED}", got "${outcome.status}" (${outcome.reason})`;
    }
    if (outcome.publicPayload !== testCase.publicPayload) {
      return "an already-redacted payload must come back unchanged";
    }
    if (outcome.key !== null) {
      return "an already-redacted payload must not produce a key — a stored one must be left alone";
    }
    return null;
  });
});

// --------------------------------------------------------------------------------------------
// The regression half — Kotlin's, but kept honest from here
// --------------------------------------------------------------------------------------------

test("no parseMustSucceed case wears a redacted discriminator", () => {
  // Those payloads exist so Kotlin can prove the ordinary and legacy formats still parse as they
  // always did. This suite does not parse them; it only keeps the array from quietly turning into
  // a second copy of the redacted list, which would make that proof vacuous.
  const known = Object.values(REDACTED_TYPE);
  checkEveryCase("parseMustSucceed", requireCases("parseMustSucceed"), (testCase) => {
    const type = JSON.parse(testCase.payload).type;
    return known.includes(type)
      ? `"${type}" is a redacted discriminator; this array is for payloads that still parse`
      : null;
  });
});

test("the Survey case is not-applicable, because there is no redacted Survey", () => {
  // RedactedQuestionContent's KDoc says so in as many words, and its four variants are built on it.
  // A survey has no right answer, so there is nothing to take off it and the payload publishes as
  // it stands.
  const surveys = requireCases("parseMustSucceed")
    .filter((c) => JSON.parse(c.payload).type === CONTENT_TYPE.SURVEY);
  assert.ok(
    surveys.length > 0,
    "No Survey case in the fixture — the claim that a survey needs no redaction is untested",
  );
  checkEveryCase("survey", surveys, (testCase) => {
    const outcome = redact(testCase.payload, "EASY", {random: seeded(1)});
    if (outcome.status !== STATUS.NOT_APPLICABLE) {
      return `expected "${STATUS.NOT_APPLICABLE}", got "${outcome.status}" (${outcome.reason})`;
    }
    if (outcome.publicPayload !== testCase.payload) {
      return "a survey must come back unchanged";
    }
    return null;
  });
});

test("redact refuses the legacy payloads the parser still accepts", () => {
  // The two formats share the `type` key, and this is the boundary between them: `single-choice`
  // is a shape the Kotlin parser reads through its legacy branch and this module does not know at
  // all. If that ever changed silently, a legacy question would be published with its answer.
  const legacy = requireCases("parseMustSucceed").filter((c) => c.legacy);
  assert.ok(legacy.length > 0, "No legacy case in the fixture — the boundary is untested");
  checkEveryCase("legacy refusal", legacy, (testCase) => {
    const outcome = redact(testCase.payload, "EASY", {random: seeded(1)});
    if (outcome.status !== STATUS.REFUSED) {
      return `expected status "${STATUS.REFUSED}", got "${outcome.status}"`;
    }
    if (outcome.publicPayload !== testCase.payload) {
      return "a refused payload must come back unchanged";
    }
    return null;
  });
});

test("every case is worth what the fixture says it is worth", () => {
  // The number that prices a lesson's reward, its unlock and its clock. Kotlin computes it a
  // second time, from the same field of the same file, so a change to either implementation that
  // is not a change to the other one lands here rather than in a player's balance.
  const cases = requireCases("redacted")
    .concat(requireCases("parseMustSucceed").filter((c) => !c.legacy));
  checkEveryCase("chars count", cases, (testCase) => {
    const payload = testCase.publicPayload || testCase.payload;
    const actual = questionCharsCount(JSON.parse(payload));
    if (actual !== testCase.expectedCharsCount) {
      return `questionCharsCount says ${actual}, the fixture says ${testCase.expectedCharsCount}`;
    }
    return null;
  });
});

test("redaction does not change what a question is worth", () => {
  // Taking the answer off must not reprice the question. The public half keeps every option,
  // item and candidate text and drops only the pointer to the right one, so the count is
  // invariant across redaction — and if a future emitter ever trimmed a text, this is where the
  // player's reward would visibly move.
  //
  // This half of the parity lives here and not in Kotlin because `source` is the emitter's raw
  // input, and several of these inputs are deliberately not valid QuestionContent — no `id`, no
  // `difficulty`, an empty one, a `MEDIUM` one — since the document supplies those downstream.
  // questionCharsCount reads a plain object and does not care; Kotlin's parser refuses them, as it
  // should. Kotlin asserts the public half against `expectedCharsCount` instead.
  checkEveryCase("redaction repricing", requireCases("redacted"), (testCase) => {
    const before = questionCharsCount(testCase.source);
    const after = questionCharsCount(JSON.parse(testCase.publicPayload));
    if (before !== after) {
      return `the full question is worth ${before}, its public half ${after}`;
    }
    return null;
  });
});

test("only the legacy cases may omit an expected chars count", () => {
  // A missing field is a silently unasserted case. The legacy payload is the one shape whose
  // count is not a property of the payload alone — its `options` are bare strings with no `text`,
  // so this module reads it as 0 while the Kotlin parser synthesises texts from the fallbacks it
  // is handed. Every other case must carry the field.
  const all = requireCases("redacted").concat(requireCases("parseMustSucceed"));
  const missing = all.filter((c) => c.expectedCharsCount === undefined);
  const legacy = all.filter((c) => c.legacy);
  assert.ok(legacy.length > 0, "No legacy case in the fixture — the exemption would be untested");
  assert.deepStrictEqual(
    missing.map((c) => c.name).sort(),
    legacy.map((c) => c.name).sort(),
    "The cases with no expectedCharsCount must be exactly the legacy ones",
  );
});

let failures = 0;
for (const [name, fn] of SUITE) {
  try {
    fn();
  } catch (error) {
    failures += 1;
    console.error(`FAILED: ${name}`);
    console.error(error.message);
  }
}
if (failures > 0) {
  console.error(`question-redaction-wire.test.js: ${failures} of ${SUITE.length} cases failed`);
  process.exitCode = 1;
} else {
  console.log(`question-redaction-wire.test.js OK (${SUITE.length} cases)`);
}
