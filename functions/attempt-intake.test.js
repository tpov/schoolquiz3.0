"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");

const {HttpsError} = require("firebase-functions/v2/https");
const {recomputePercentScore, isWellFormedCodeAnswer, attemptActivityCounts} = require("./result-verification");
const {FAULT, UNSCORABLE, scoreAttempt} = require("./attempt-scoring");
const {SINGLE} = require("./_question-fixtures");
const {
  SCORING_AUTHORITY,
  PAYMENT_RULE,
  REJECTION,
  MAX_CODE_ANSWER_CHARS,
  MAX_SERVED_ENTRIES,
  MAX_SERVED_POSITION,
  MAX_QUESTION_ID_CHARS,
  readSubmittedAttempt,
  isPayable,
  withServerScore,
} = require("./attempt-intake");

/**
 * Three things are proved here, and they are kept apart because they fail for different reasons.
 *
 * 1. **The device-scored path is today's path, to the byte.** `index.js` cannot be required from a
 *    plain node test — it raises firebase-admin on load — so today's normaliser is *rebuilt* here:
 *    a frozen copy of `normalizeLessonResultAttemptEvent` (below, verbatim), closing over helpers
 *    whose source text is cut out of `index.js` at test time. Both are then run over one fixture set
 *    that reaches every rejection today produces, and the outcomes are compared: the same error
 *    with the same code and message, or the same fields with the same values in the same order.
 *    The frozen copy is also compared against `index.js` itself, so that "today" cannot drift
 *    quietly: when the intake is swapped over to this module, that one assertion is what the wiring
 *    step retires, on purpose.
 *
 * 2. **Every row of the spec's matrix**, as named cases against the module alone.
 *
 * 3. **The payment rule is data the scorer's output satisfies or not**, proved by feeding what this
 *    module returns straight into `scoreAttempt` rather than into a stand-in for it.
 */

const UID = "uid-1";
const FIXED_NOW = 1_700_000_000_000;

const INDEX_SOURCE = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
const INTAKE_SOURCE = fs.readFileSync(path.join(__dirname, "attempt-intake.js"), "utf8");

// ─── Cutting functions out of source text ───────────────────────────────────────────────────────

/**
 * The text of a top-level `function name(...) { ... }`, from `function` to its closing brace.
 *
 * Every function looked up here is declared at column 0 with its body indented, so the first
 * `}` at column 0 after the header closes it. Deliberately that simple: a brace matcher would be
 * more general and would also be a second thing to be wrong about.
 */
function extractFunction(source, name) {
  const header = `\nfunction ${name}(`;
  const start = source.indexOf(header);
  assert.notStrictEqual(start, -1, `function ${name} is not declared at top level in the source given`);
  const end = source.indexOf("\n}\n", start);
  assert.notStrictEqual(end, -1, `function ${name} never closes at column 0`);
  return source.slice(start + 1, end + 2);
}

/** The helpers the legacy path reads through, and which `attempt-intake.js` copies verbatim. */
const VERBATIM_HELPERS = [
  "stringValue",
  "nullableString",
  "numberValue",
  "listMaps",
  "nonNegativeEventTime",
  "normalizeScope",
  "normalizeContentEvent",
  "normalizeLessonAnswers",
];

/** The helpers as `index.js` declares them, evaluated in a scope of their own. */
function loadIndexHelpers() {
  const body = [
    "\"use strict\";",
    "const PUBLIC_SCOPE = \"public\";",
    "const PRIVATE_SCOPE = \"private\";",
    ...VERBATIM_HELPERS.map((name) => extractFunction(INDEX_SOURCE, name)),
    `return {${VERBATIM_HELPERS.join(", ")}};`,
  ].join("\n");
  // eslint-disable-next-line no-new-func
  return new Function("HttpsError", body)(HttpsError);
}

const LEGACY = loadIndexHelpers();
const {stringValue, numberValue, nonNegativeEventTime, normalizeContentEvent, normalizeLessonAnswers} = LEGACY;
// Разбор маски заявок живёт в своём чистом модуле, а не в `index.js`, поэтому берётся оттуда
// же, откуда его берёт `index.js`, — иначе замороженная копия не запустится.
const {noClaims, validateClaimMask} = require("./charge-claims");

// ─── Today's normaliser, frozen ─────────────────────────────────────────────────────────────────
//
// A verbatim copy of `normalizeLessonResultAttemptEvent` as `index.js` declares it at the spec's
// baseline, refreshed when the charge-claim mask joined the intake. It closes over the helpers
// cut out of `index.js` above, so what runs
// here is today's code and nothing of this suite's own. No line numbers are quoted: what pins this
// copy to the original is the byte comparison below, and a citation that drifts is worse than none.
//
// The module's outcome differs from this copy's in exactly one class of body, tested by name below:
// a body carrying no digits — the key absent, or the key present holding `null` — is no longer read
// as `codeAnswer: ""`. Every body that sends a real string or a real number is identical.
//
// Do not edit. `testTheFrozenReferenceIsStillTodaysIntake` compares this text against `index.js`.

function normalizeLessonResultAttemptEvent(data, authUid) {
  const userId = stringValue(data.userId, authUid);
  if (userId !== authUid) {
    throw new HttpsError("permission-denied", "Attempt userId must match authenticated uid");
  }
  const event = normalizeContentEvent(data, authUid);
  const attemptId = stringValue(data.attemptId);
  if (!attemptId) throw new HttpsError("invalid-argument", "attemptId is required");
  const percentScore = numberValue(data.percentScore, null);
  if (percentScore === null || percentScore < 0 || percentScore > 100) {
    throw new HttpsError("invalid-argument", "percentScore must be in 0..100");
  }
  const codeAnswer = stringValue(data.codeAnswer);
  if (!isWellFormedCodeAnswer(codeAnswer)) {
    throw new HttpsError("invalid-argument", "codeAnswer must contain digits only");
  }
  const difficulty = stringValue(data.difficulty, "EASY").toUpperCase();
  if (difficulty !== "EASY" && difficulty !== "HARD") {
    throw new HttpsError("invalid-argument", "difficulty must be EASY or HARD");
  }
  // The client always derives percentScore from codeAnswer (CompleteAttemptUseCase and
  // AbortAttemptUseCase are the only two paths), so an honest attempt always matches.
  // A mismatch means the payload was crafted: keep the event for analysis, pay nothing.
  const expectedPercentScore = recomputePercentScore(codeAnswer);
  const answers = normalizeLessonAnswers(data.answers);
  // Заявки на заряды — строкой той же длины рядом с цифрами. Испорченная маска отвергается сразу:
  // это не перерасход, а искажённый payload, и платить по нему частично нельзя (CAP-3).
  const chargeClaims = stringValue(data.chargeClaims) || noClaims(codeAnswer.length);
  const claimFault = validateClaimMask(chargeClaims, codeAnswer, difficulty);
  if (claimFault) {
    throw new HttpsError("invalid-argument", `chargeClaims is malformed: ${claimFault}`);
  }
  return {
    answers,
    ...event,
    attemptId,
    difficulty,
    codeAnswer,
    chargeClaims,
    percentScore,
    expectedPercentScore,
    scoreVerified: expectedPercentScore === percentScore,
    completedAtMs: nonNegativeEventTime(data.completedAtMs),
    createdAtMs: nonNegativeEventTime(data.createdAtMs),
  };
}
/** Every message today's intake can refuse an attempt with. The fixture set must reach them all. */
const TODAYS_REJECTIONS = [
  "Attempt userId must match authenticated uid",
  "Private event ownerUid must match authenticated uid",
  "catalogId, questId, sectionId, themeId, and lessonId are required",
  "attemptId is required",
  "percentScore must be in 0..100",
  "codeAnswer must contain digits only",
  "difficulty must be EASY or HARD",
];

// ─── Harness ────────────────────────────────────────────────────────────────────────────────────

const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

/** `nonNegativeEventTime` falls back to `Date.now()`; pinned so both sides read the same clock. */
function withFixedClock(fn) {
  const realNow = Date.now;
  Date.now = () => FIXED_NOW;
  try {
    return fn();
  } finally {
    Date.now = realNow;
  }
}

function outcomeOf(fn) {
  try {
    return {value: fn()};
  } catch (error) {
    return {error};
  }
}

function expectRejection(fn, message, code = "invalid-argument") {
  const outcome = outcomeOf(fn);
  assert.ok(outcome.error, `expected a rejection "${message}", got ${JSON.stringify(outcome.value)}`);
  assert.ok(outcome.error instanceof HttpsError, `expected an HttpsError, got ${outcome.error}`);
  assert.strictEqual(outcome.error.code, code);
  assert.strictEqual(outcome.error.message, message);
}

function pick(object, keys) {
  const picked = {};
  for (const key of keys) picked[key] = object[key];
  return picked;
}

// ─── Fixtures ───────────────────────────────────────────────────────────────────────────────────

/** 9 → 100, 5 → 50, 1 → 0, 9 → 100: 250 over four shown, floored to 62. */
const DIGITS = "9519";
const PERCENT = 62;

const ANSWER_ROWS = [
  {
    questionId: "q0",
    codeAnswerIndex: 0,
    score: 9,
    answerPayload: "{\"type\":\"single-choice\",\"selected\":\"b\"}",
    answeredAtMs: FIXED_NOW - 5000,
    durationMs: 4200,
    wasTimeout: false,
  },
  {questionId: "q1", codeAnswerIndex: 1, score: 5, answerPayload: "{}", answeredAtMs: FIXED_NOW - 4000, durationMs: 900},
];

/** The four positions of `DIGITS` are all shown, so this is the one served list that agrees with it. */
const SERVED_FOR_DIGITS = [0, 1, 2, 3].map((at) => ({codeAnswerIndex: at, questionId: `q${at}`}));

function body(overrides = {}) {
  const base = {
    userId: UID,
    scope: "public",
    catalogId: "catalog-1",
    questId: "quest-1",
    sectionId: "section-1",
    themeId: "theme-1",
    lessonId: "lesson-1",
    lessonVersion: 3,
    sourceShelf: "arena",
    attemptId: "attempt-1",
    difficulty: "EASY",
    codeAnswer: DIGITS,
    percentScore: PERCENT,
    answers: ANSWER_ROWS,
    completedAtMs: FIXED_NOW - 1000,
    createdAtMs: FIXED_NOW - 60_000,
  };
  const result = {...base, ...overrides};
  // `undefined` in an override means "leave the key out", the way a wire body leaves it out.
  for (const key of Object.keys(result)) if (result[key] === undefined) delete result[key];
  return result;
}

/** A server-scored body: hard, neither digits nor percent, served present. */
function serverScoredBody(overrides = {}) {
  return body({difficulty: "HARD", codeAnswer: undefined, percentScore: undefined, served: SERVED_FOR_DIGITS, ...overrides});
}

/**
 * The bodies both intakes are run over. Everything today accepts, in every variant the helpers
 * treat differently, and everything today refuses, including bodies with several faults at once
 * so that the *order* of the checks is compared and not only their presence.
 */
const BYTE_FOR_BYTE_FIXTURES = [
  // ── accepted ──
  ["easy, verified", body()],
  ["hard, spelled in lower case", body({difficulty: "hard"})],
  ["difficulty absent reads as easy", body({difficulty: undefined})],
  ["difficulty null reads as easy", body({difficulty: null})],
  ["difficulty empty reads as easy", body({difficulty: ""})],
  ["private scope with the caller as owner", body({scope: "private", ownerUid: UID, sourceShelf: undefined})],
  ["public scope drops an ownerUid", body({scope: "public", ownerUid: "someone-else"})],
  ["an unknown scope reads as public", body({scope: "weird", ownerUid: UID})],
  ["sourceShelf absent on a public body", body({sourceShelf: undefined})],
  ["lessonVersion below one is clamped", body({lessonVersion: 0})],
  ["lessonVersion as a string", body({lessonVersion: "7"})],
  ["lessonVersion absent", body({lessonVersion: undefined})],
  ["userId absent falls back to the caller", body({userId: undefined})],
  ["percent as a numeric string", body({percentScore: String(PERCENT)})],
  ["percent as a Firestore-like number", body({percentScore: {toNumber: () => PERCENT}})],
  ["codeAnswer as a number", body({codeAnswer: Number(DIGITS)})],
  ["percent 0 on an empty codeAnswer", body({codeAnswer: "", percentScore: 0})],
  ["percent 100 on one right answer", body({codeAnswer: "9", percentScore: 100})],
  ["nothing shown at all", body({codeAnswer: "0000", percentScore: 0})],
  // A null percent beside real digits still reads as today's 0 — the digits decide the kind, and
  // once the kind is device-scored nothing about today's reading of the body changes.
  ["a null percent beside real digits", body({percentScore: null})],
  ["a crafted percent is kept and marked", body({percentScore: 99})],
  ["a crafted percent of zero", body({percentScore: 0})],
  ["fractional percent", body({percentScore: 62.5})],
  ["answers absent", body({answers: undefined})],
  ["answers not a list", body({answers: "nope"})],
  ["answers with junk rows and a nameless row", body({answers: [null, "x", 7, {score: 9}, ...ANSWER_ROWS]})],
  ["answers clamped field by field", body({answers: [{
    questionId: "q2",
    codeAnswerIndex: -3,
    score: 15,
    answerPayload: null,
    answeredAtMs: -4,
    durationMs: -5,
    wasTimeout: "yes",
  }, {questionId: "q3", score: -1}]})],
  ["times absent fall back to the clock", body({completedAtMs: undefined, createdAtMs: undefined})],
  ["times negative are floored", body({completedAtMs: -1, createdAtMs: -100})],
  ["times as strings", body({completedAtMs: "1700000000500", createdAtMs: "1700000000400"})],
  ["served agreeing with the digits", body({served: SERVED_FOR_DIGITS})],
  ["served null reads as absent", body({served: null})],
  ["served empty on nothing shown", body({codeAnswer: "0000", percentScore: 0, served: [], answers: []})],
  // Kept and marked, not refused: the byte-for-byte comparison covers the fields today produces,
  // and `servedVerified` is asserted by name in the matrix below.
  ["served disagreeing with the digits", body({served: SERVED_FOR_DIGITS.slice(0, 2)})],
  ["an answer row the served list does not hold", body({served: SERVED_FOR_DIGITS, answers: [
    {questionId: "q9", codeAnswerIndex: 3, score: 9, answerPayload: "{}", answeredAtMs: FIXED_NOW, durationMs: 1},
  ]})],

  // ── refused, one fault each ──
  ["userId of another account", body({userId: "uid-2"})],
  ["private scope owned by another account", body({scope: "private", ownerUid: "uid-2"})],
  ["private scope with no owner", body({scope: "private", ownerUid: undefined})],
  ["catalogId missing", body({catalogId: undefined})],
  ["questId missing", body({questId: ""})],
  ["sectionId missing", body({sectionId: null})],
  ["themeId missing", body({themeId: undefined})],
  ["lessonId missing", body({lessonId: undefined})],
  ["attemptId missing", body({attemptId: undefined})],
  ["attemptId empty", body({attemptId: ""})],
  ["attemptId null", body({attemptId: null})],
  ["percent missing", body({percentScore: undefined})],
  ["percent below zero", body({percentScore: -1})],
  ["percent above a hundred", body({percentScore: 101})],
  ["percent not a number", body({percentScore: "lots"})],
  ["percent NaN", body({percentScore: NaN})],
  ["percent Infinity", body({percentScore: Infinity})],
  ["digits with a letter", body({codeAnswer: "95a9"})],
  ["digits with a point", body({codeAnswer: "9.5"})],
  ["digits with a sign", body({codeAnswer: "-9"})],
  ["digits with a space", body({codeAnswer: " 9"})],
  ["digits as an object", body({codeAnswer: {}})],
  ["difficulty unknown", body({difficulty: "MEDIUM"})],
  ["difficulty numeric", body({difficulty: 5})],

  // ── refused, several faults at once: the first check wins, in today's order ──
  ["foreign userId and missing catalogId", body({userId: "uid-2", catalogId: undefined})],
  ["foreign owner and missing questId", body({scope: "private", ownerUid: "uid-2", questId: undefined})],
  ["missing lessonId and missing attemptId", body({lessonId: undefined, attemptId: undefined})],
  ["missing attemptId and bad percent", body({attemptId: undefined, percentScore: 200})],
  ["bad percent and bad digits", body({percentScore: 200, codeAnswer: "abc"})],
  ["bad digits and bad difficulty", body({codeAnswer: "abc", difficulty: "MEDIUM"})],
  ["bad difficulty and a crafted percent", body({difficulty: "MEDIUM", percentScore: 1})],
  ["every field wrong at once", body({
    userId: "uid-2", scope: "private", ownerUid: "uid-3", catalogId: undefined, attemptId: "",
    percentScore: -5, codeAnswer: "x", difficulty: "MEDIUM",
  })],
];

// ═══ 1. Today's path, to the byte ═══════════════════════════════════════════════════════════════

test("the verbatim helper copies in attempt-intake.js match index.js byte for byte", () => {
  for (const name of VERBATIM_HELPERS) {
    assert.strictEqual(
      extractFunction(INTAKE_SOURCE, name),
      extractFunction(INDEX_SOURCE, name),
      `${name} in attempt-intake.js has drifted from index.js`,
    );
  }
  for (const line of ["const PUBLIC_SCOPE = \"public\";", "const PRIVATE_SCOPE = \"private\";"]) {
    assert.ok(INDEX_SOURCE.includes(line), `index.js no longer declares ${line}`);
    assert.ok(INTAKE_SOURCE.includes(line), `attempt-intake.js no longer declares ${line}`);
  }
});

// Сравнение замороженной копии с `index.js` снято: приёмник переключён на `readSubmittedAttempt`,
// и в `index.js` прежней функции больше нет. Копия остаётся эталоном ДО переключения — по ней
// сверяются побайтовые фикстуры ниже, и в этом теперь весь её смысл. Так и предписывало примечание
// над ней самой.

test("every body today accepts or refuses comes out identical, field for field and message for message", () => {
  const reached = new Set();
  let accepted = 0;
  withFixedClock(() => {
    for (const [name, data] of BYTE_FOR_BYTE_FIXTURES) {
      const legacy = outcomeOf(() => normalizeLessonResultAttemptEvent(data, UID));
      const modern = outcomeOf(() => readSubmittedAttempt(data, UID));

      if (legacy.error) {
        reached.add(legacy.error.message);
        assert.ok(modern.error, `${name}: today refuses ("${legacy.error.message}"), the module accepted`);
        assert.strictEqual(modern.error.constructor, legacy.error.constructor, `${name}: error type differs`);
        assert.strictEqual(modern.error.code, legacy.error.code, `${name}: error code differs`);
        assert.strictEqual(modern.error.message, legacy.error.message, `${name}: message differs`);
        continue;
      }

      accepted += 1;
      assert.ok(!modern.error, `${name}: today accepts, the module refused with "${modern.error && modern.error.message}"`);
      const legacyKeys = Object.keys(legacy.value);
      const modernKeys = Object.keys(modern.value);
      assert.deepStrictEqual(modernKeys.slice(0, legacyKeys.length), legacyKeys, `${name}: field order differs`);
      assert.deepStrictEqual(
        modernKeys.slice(legacyKeys.length),
        ["scoringAuthority", "served", "servedVerified", "paymentRule"],
        `${name}: the module adds fields other than the four it declares`,
      );
      const shared = pick(modern.value, legacyKeys);
      assert.deepStrictEqual(shared, legacy.value, `${name}: a field's value differs`);
      assert.strictEqual(JSON.stringify(shared), JSON.stringify(legacy.value), `${name}: serialised form differs`);
      assert.strictEqual(modern.value.scoringAuthority, SCORING_AUTHORITY.CLIENT, name);
      assert.strictEqual(modern.value.paymentRule, PAYMENT_RULE.DEVICE_SCORED, name);
    }
  });
  assert.ok(accepted >= 20, `only ${accepted} accepting fixtures; the set is meant to cover every helper branch`);
  assert.deepStrictEqual(
    [...reached].sort(),
    [...TODAYS_REJECTIONS].sort(),
    "the fixture set does not reach every rejection today produces",
  );
});

test("the one deliberate difference: a body with no digits is never read as empty digits", () => {
  // Today reads a missing codeAnswer as "" and accepts. The spec's matrix names that body — a
  // percent without digits — as refused. `null` is the same body: it is how anything map-based
  // serialises an absent value, and reading it as a present "" is what made an empty hard attempt
  // come out verified and payable. Both forms are refused; a real string is read exactly as today.
  for (const missing of [{codeAnswer: undefined}, {codeAnswer: null}]) {
    const data = body(missing);
    withFixedClock(() => {
      const legacy = outcomeOf(() => normalizeLessonResultAttemptEvent(data, UID));
      assert.ok(legacy.value, `today accepts ${JSON.stringify(missing)} beside a percent`);
      assert.strictEqual(legacy.value.codeAnswer, "");
      assert.strictEqual(legacy.value.scoreVerified, false);
    });
    expectRejection(() => readSubmittedAttempt(data, UID), REJECTION.CODE_ANSWER_REQUIRED_WITH_PERCENT());
  }
  assert.strictEqual(REJECTION.CODE_ANSWER_REQUIRED_WITH_PERCENT(), "codeAnswer is required when percentScore is sent");
});

test("null is not 'present': a null codeAnswer and percent mean no digits, on both difficulties", () => {
  const nulls = {codeAnswer: null, percentScore: null};

  // What today does with that body, on both difficulties: reads it as an empty verified attempt.
  withFixedClock(() => {
    for (const difficulty of ["EASY", "HARD"]) {
      const legacy = normalizeLessonResultAttemptEvent(body({...nulls, difficulty}), UID);
      assert.strictEqual(legacy.codeAnswer, "");
      assert.strictEqual(legacy.percentScore, 0);
      assert.strictEqual(legacy.scoreVerified, true, "verified — and therefore paid and charged");
    }
  });

  // EASY: easy questions carry their answers, so a body with nothing to score is a defect.
  expectRejection(() => readSubmittedAttempt(body({...nulls}), UID), REJECTION.EASY_WITHOUT_DIGITS());
  expectRejection(
    () => readSubmittedAttempt(body({...nulls, difficulty: "EASY", served: SERVED_FOR_DIGITS}), UID),
    REJECTION.EASY_WITHOUT_DIGITS(),
  );

  // HARD with served: the server scores it. Not a verified empty attempt, and not payable on sight.
  const hard = withFixedClock(() => readSubmittedAttempt(body({...nulls, difficulty: "HARD", served: SERVED_FOR_DIGITS}), UID));
  assert.strictEqual(hard.scoringAuthority, SCORING_AUTHORITY.SERVER);
  assert.strictEqual(hard.paymentRule, PAYMENT_RULE.SERVER_SCORED);
  assert.strictEqual(hard.codeAnswer, null);
  assert.ok(!("scoreVerified" in hard), "there is no claim to verify");
  assert.strictEqual(isPayable(hard), false);

  // HARD without served: nothing to score against.
  expectRejection(() => readSubmittedAttempt(body({...nulls, difficulty: "HARD"}), UID), REJECTION.HARD_WITHOUT_SERVED());
});

// ═══ 2. The matrix ══════════════════════════════════════════════════════════════════════════════

test("device-scored, as today: digits and a percent, no served", () => {
  const attempt = readSubmittedAttempt(body(), UID);
  assert.strictEqual(attempt.scoringAuthority, SCORING_AUTHORITY.CLIENT);
  assert.strictEqual(attempt.served, null, "no served list means unknown, and unknown is null");
  assert.strictEqual(attempt.paymentRule, PAYMENT_RULE.DEVICE_SCORED);
  assert.strictEqual(attempt.codeAnswer, DIGITS);
  assert.strictEqual(attempt.percentScore, PERCENT);
  assert.strictEqual(attempt.expectedPercentScore, recomputePercentScore(DIGITS));
  assert.strictEqual(attempt.scoreVerified, true);
  assert.strictEqual(attempt.difficulty, "EASY");
  assert.strictEqual(attempt.userId, UID);
});

test("device-scored, hard: the difficulty does not change who scored it", () => {
  const attempt = readSubmittedAttempt(body({difficulty: "HARD"}), UID);
  assert.strictEqual(attempt.scoringAuthority, SCORING_AUTHORITY.CLIENT);
  assert.strictEqual(attempt.difficulty, "HARD");
  assert.strictEqual(attempt.scoreVerified, true);
});

test("device-scored with served: accepted, validated, and reduced to its two fields", () => {
  const sent = SERVED_FOR_DIGITS.map((entry) => ({...entry, extra: "ignored", score: 9}));
  const frozen = JSON.stringify(sent);
  const attempt = readSubmittedAttempt(body({served: sent}), UID);
  assert.deepStrictEqual(attempt.served, SERVED_FOR_DIGITS);
  assert.strictEqual(JSON.stringify(sent), frozen, "the input list was mutated");
  assert.notStrictEqual(attempt.served, sent, "the returned list is the caller's own array");
  assert.strictEqual(attempt.scoringAuthority, SCORING_AUTHORITY.CLIENT);
  assert.strictEqual(attempt.scoreVerified, true);
});

/**
 * One policy for a client that lies.
 *
 * A percent that does not follow from the digits has always been *kept and marked* —
 * `scoreVerified: false`, the event stored for analysis, nothing paid and nothing charged. A served
 * list that does not follow from the digits is the same class of lie, and used to be an
 * `invalid-argument` that threw out of the whole call and lost the evidence. It is now marked the
 * same way, in the same place, with the same consequence.
 */
test("a served list that disagrees with the digits is kept and marked, not thrown away", () => {
  const cases = {
    // "9019": position 1 was never shown, yet served claims it.
    "a served position whose digit is '0'": body({
      codeAnswer: "9019",
      percentScore: 66,
      served: [0, 1, 2, 3].map((at) => ({codeAnswerIndex: at, questionId: `q${at}`})),
      answers: [],
    }),
    "a served position past the end of the digits":
      body({served: [...SERVED_FOR_DIGITS, {codeAnswerIndex: 7, questionId: "q7"}]}),
    "a shown digit the served list does not name":
      body({served: SERVED_FOR_DIGITS.filter((entry) => entry.codeAnswerIndex !== 2)}),
    "an empty list against shown digits": body({served: [], answers: []}),
    // "9019" shows 0, 2, 3; served says 0, 1, 3 — wrong in both directions at once.
    "lists that disagree in several places": body({
      codeAnswer: "9019",
      percentScore: 66,
      served: [0, 1, 3].map((at) => ({codeAnswerIndex: at, questionId: `q${at}`})),
      answers: [],
    }),
  };
  for (const [what, data] of Object.entries(cases)) {
    const attempt = withFixedClock(() => readSubmittedAttempt(data, UID));
    assert.strictEqual(attempt.servedVerified, false, what);
    assert.strictEqual(attempt.scoringAuthority, SCORING_AUTHORITY.CLIENT, what);
    assert.deepStrictEqual(attempt.served, data.served, `${what}: the list itself is kept for analysis`);
    assert.strictEqual(attempt.codeAnswer, data.codeAnswer, `${what}: so are the digits`);
    assert.strictEqual(isPayable(attempt), false, `${what}: and nothing is paid on it`);
  }
  // The percent lie and the served lie land in the same place, with the same effect on payment.
  const craftedPercent = readSubmittedAttempt(body({percentScore: 99}), UID);
  assert.strictEqual(craftedPercent.scoreVerified, false);
  assert.strictEqual(craftedPercent.servedVerified, true);
  assert.strictEqual(isPayable(craftedPercent), false);
});

test("an answer row the served list does not hold marks the attempt, on position as well as on id", () => {
  // Sync-contract rule 3: every answers[] row names a (questionId, codeAnswerIndex) pair that
  // `served` holds. Nothing enforced it on this path at all until now.
  const row = (questionId, codeAnswerIndex) =>
    ({questionId, codeAnswerIndex, score: 9, answerPayload: "{}", answeredAtMs: FIXED_NOW, durationMs: 1});
  const cases = {
    "a question that was never dealt": [row("q9", 3)],
    "the right question at the wrong position": [row("q1", 2)],
    "one honest row and one invented": [row("q0", 0), row("q9", 1)],
  };
  for (const [what, answers] of Object.entries(cases)) {
    const attempt = withFixedClock(() => readSubmittedAttempt(body({served: SERVED_FOR_DIGITS, answers}), UID));
    assert.strictEqual(attempt.servedVerified, false, what);
    assert.strictEqual(attempt.scoreVerified, true, `${what}: the percent is untouched by this`);
    assert.strictEqual(isPayable(attempt), false, what);
  }
  // The honest shape: every row sits where served says its question sat.
  const honest = withFixedClock(() => readSubmittedAttempt(body({served: SERVED_FOR_DIGITS}), UID));
  assert.strictEqual(honest.servedVerified, true);
  assert.strictEqual(isPayable(honest), true);
  // A row `normalizeLessonAnswers` drops cannot mark anything: it is not in `answers` to be judged.
  const dropped = withFixedClock(() => readSubmittedAttempt(
    body({served: SERVED_FOR_DIGITS, answers: [...ANSWER_ROWS, {questionId: "", codeAnswerIndex: 9}]}),
    UID,
  ));
  assert.strictEqual(dropped.servedVerified, true);
});

test("no served list is not a disagreement: a client from before E2.10 is verified as today", () => {
  const attempt = readSubmittedAttempt(body(), UID);
  assert.strictEqual(attempt.served, null);
  assert.strictEqual(attempt.servedVerified, true, "unknown is not a lie");
  assert.strictEqual(isPayable(attempt), true);
  const explicitNull = readSubmittedAttempt(body({served: null}), UID);
  assert.strictEqual(explicitNull.served, null);
  assert.strictEqual(explicitNull.servedVerified, true);
});

test("a body that cannot be read is still invalid-argument, not kept and marked", () => {
  // The line between the two policies: a list nobody can interpret leaves nothing to infer, so
  // there is nothing to keep and nothing to mark. Only a *readable* list that disagrees is kept.
  for (const served of [{}, "q0", [{codeAnswerIndex: "0", questionId: "q0"}], [null]]) {
    const outcome = outcomeOf(() => readSubmittedAttempt(body({served}), UID));
    assert.ok(outcome.error, `${JSON.stringify(served)} was accepted`);
    assert.strictEqual(outcome.error.code, "invalid-argument", JSON.stringify(served));
  }
});

test("device-scored with served: a missing position is not clamped to zero", () => {
  // Position 0 *is* shown here, so an inherited `Math.max(0, …)` would have let this entry through
  // as position 0. It has no position at all, and is refused as such.
  expectRejection(
    () => readSubmittedAttempt(body({codeAnswer: "9", percentScore: 100, served: [{questionId: "q0"}]}), UID),
    REJECTION.SERVED_POSITION_INVALID(0),
  );
});

test("device-scored with served: today's rejections keep precedence over the served checks", () => {
  const broken = [{codeAnswerIndex: "0", questionId: "q0"}];
  expectRejection(() => readSubmittedAttempt(body({percentScore: 200, served: broken}), UID), "percentScore must be in 0..100");
  expectRejection(() => readSubmittedAttempt(body({codeAnswer: "x", served: broken}), UID), "codeAnswer must contain digits only");
  expectRejection(() => readSubmittedAttempt(body({difficulty: "MEDIUM", served: broken}), UID), "difficulty must be EASY or HARD");
  expectRejection(() => readSubmittedAttempt(body({attemptId: "", served: broken}), UID), "attemptId is required");
});

test("device-scored with served: an unreadable list is refused even when it also disagrees", () => {
  // Unsorted *and* disagreeing with the digits. Disagreement alone is kept and marked; a list that
  // cannot be read is refused, and being unreadable is decided first.
  const served = [{codeAnswerIndex: 3, questionId: "q3"}, {codeAnswerIndex: 0, questionId: "q0"}];
  expectRejection(
    () => readSubmittedAttempt(body({codeAnswer: "9000", percentScore: 100, served}), UID),
    REJECTION.SERVED_OUT_OF_ORDER(1, 0, 3),
  );
});

test("server-scored: hard, no digits, no percent, served present", () => {
  const data = serverScoredBody();
  const attempt = withFixedClock(() => readSubmittedAttempt(data, UID));
  assert.strictEqual(attempt.scoringAuthority, SCORING_AUTHORITY.SERVER);
  assert.strictEqual(attempt.paymentRule, PAYMENT_RULE.SERVER_SCORED);
  assert.deepStrictEqual(attempt.served, SERVED_FOR_DIGITS);
  assert.strictEqual(attempt.codeAnswer, null, "no digits until the server scores it");
  assert.strictEqual(attempt.percentScore, null, "no percent until the server scores it");
  assert.ok(!("scoreVerified" in attempt), "scoreVerified is not applicable and must not be present at all");
  assert.ok(!("servedVerified" in attempt), "nor is servedVerified: there are no digits to disagree with");
  assert.ok(!("expectedPercentScore" in attempt), "there is no claim to recompute against");
  assert.strictEqual(attempt.difficulty, "HARD");
  assert.strictEqual(attempt.attemptId, "attempt-1");
  // The shared fields are read by the very same helpers as today.
  withFixedClock(() => {
    const event = normalizeContentEvent(data, UID);
    for (const key of Object.keys(event)) assert.deepStrictEqual(attempt[key], event[key], `event field ${key}`);
    assert.deepStrictEqual(attempt.answers, normalizeLessonAnswers(data.answers));
    assert.strictEqual(attempt.completedAtMs, nonNegativeEventTime(data.completedAtMs));
    assert.strictEqual(attempt.createdAtMs, nonNegativeEventTime(data.createdAtMs));
  });
  assert.deepStrictEqual(Object.keys(attempt), [
    "answers", "userId", "scope", "ownerUid", "catalogId", "questId", "sectionId", "themeId", "lessonId",
    "lessonVersion", "sourceShelf", "attemptId", "difficulty", "codeAnswer", "percentScore",
    "completedAtMs", "createdAtMs", "scoringAuthority", "served", "paymentRule",
  ]);
});

test("server-scored: the difficulty is forgiven its case, and an empty served list is legal", () => {
  const lower = readSubmittedAttempt(serverScoredBody({difficulty: "hard"}), UID);
  assert.strictEqual(lower.scoringAuthority, SCORING_AUTHORITY.SERVER);
  assert.strictEqual(lower.difficulty, "HARD");
  const nothingShown = readSubmittedAttempt(serverScoredBody({served: []}), UID);
  assert.deepStrictEqual(nothingShown.served, []);
  assert.strictEqual(nothingShown.scoringAuthority, SCORING_AUTHORITY.SERVER);
});

test("server-scored: a sparse served list — the honest shape of a hard attempt — is accepted in order", () => {
  // Every other accepted fixture in this suite is dense 0..n-1, and a rule "reject unless the
  // positions are 0..n-1" would have passed all of them. The real hard attempt deals a subset of a
  // larger eligible pool: 20 of 30, at whatever positions the pool put them.
  const served = [0, 7, 19].map((at) => ({codeAnswerIndex: at, questionId: `q${at}`}));
  const attempt = readSubmittedAttempt(serverScoredBody({served}), UID);
  assert.strictEqual(attempt.scoringAuthority, SCORING_AUTHORITY.SERVER);
  assert.deepStrictEqual(attempt.served, served, "the gaps are the point; nothing is compacted");
  assert.deepStrictEqual(attempt.served.map((entry) => entry.codeAnswerIndex), [0, 7, 19]);
  // A list that starts past zero is just as honest — the first eligible question need not be dealt.
  const late = readSubmittedAttempt(serverScoredBody({
    served: [{codeAnswerIndex: 4, questionId: "q4"}, {codeAnswerIndex: 5, questionId: "q5"}],
  }), UID);
  assert.deepStrictEqual(late.served.map((entry) => entry.codeAnswerIndex), [4, 5]);
});

test("server-scored: a difficulty that is not a string is refused, on the new path only", () => {
  // `stringValue(["hard"])` is "hard", and every existing client is judged by that reading — so it
  // stays untouched on the device-scored path, byte for byte. Nothing has ever sent this one a
  // non-string, so it says so instead of guessing.
  assert.strictEqual(REJECTION.DIFFICULTY_NOT_A_STRING(), "difficulty must be sent as a string");
  for (const difficulty of [["hard"], 5, {toString: () => "HARD"}, true]) {
    expectRejection(
      () => readSubmittedAttempt(serverScoredBody({difficulty}), UID),
      REJECTION.DIFFICULTY_NOT_A_STRING(),
    );
  }
  // Absent and null still read as EASY, and are refused as an easy attempt with no digits.
  for (const difficulty of [undefined, null]) {
    expectRejection(() => readSubmittedAttempt(serverScoredBody({difficulty}), UID), REJECTION.EASY_WITHOUT_DIGITS());
  }
  // The device-scored path keeps today's reading exactly.
  withFixedClock(() => {
    const data = body({difficulty: ["hard"]});
    const legacy = normalizeLessonResultAttemptEvent(data, UID);
    const modern = readSubmittedAttempt(data, UID);
    assert.strictEqual(legacy.difficulty, "HARD");
    assert.strictEqual(modern.difficulty, "HARD");
  });
});

test("server-scored: today's shared checks still apply, with today's messages", () => {
  expectRejection(
    () => readSubmittedAttempt(serverScoredBody({userId: "uid-2"}), UID),
    "Attempt userId must match authenticated uid",
    "permission-denied",
  );
  expectRejection(
    () => readSubmittedAttempt(serverScoredBody({scope: "private", ownerUid: "uid-2"}), UID),
    "Private event ownerUid must match authenticated uid",
    "permission-denied",
  );
  expectRejection(
    () => readSubmittedAttempt(serverScoredBody({themeId: undefined}), UID),
    "catalogId, questId, sectionId, themeId, and lessonId are required",
  );
  expectRejection(() => readSubmittedAttempt(serverScoredBody({attemptId: undefined}), UID), "attemptId is required");
  expectRejection(
    () => readSubmittedAttempt(serverScoredBody({difficulty: "MEDIUM"}), UID),
    "difficulty must be EASY or HARD",
  );
});

test("easy without digits is refused: easy questions carry their answers and the device scores them", () => {
  const message = REJECTION.EASY_WITHOUT_DIGITS();
  assert.strictEqual(message, "an EASY attempt must carry codeAnswer and percentScore");
  expectRejection(() => readSubmittedAttempt(serverScoredBody({difficulty: "EASY"}), UID), message);
  expectRejection(() => readSubmittedAttempt(serverScoredBody({difficulty: "easy"}), UID), message);
  expectRejection(() => readSubmittedAttempt(serverScoredBody({difficulty: undefined}), UID), message);
  expectRejection(() => readSubmittedAttempt(serverScoredBody({difficulty: null}), UID), message);
  // With or without a served list — the list does not make an easy attempt scorable here.
  expectRejection(() => readSubmittedAttempt(serverScoredBody({difficulty: "EASY", served: undefined}), UID), message);
});

test("hard without digits and without served is refused: nothing to score against", () => {
  const message = REJECTION.HARD_WITHOUT_SERVED();
  assert.strictEqual(message, "a HARD attempt without codeAnswer must carry served");
  expectRejection(() => readSubmittedAttempt(serverScoredBody({served: undefined}), UID), message);
  expectRejection(() => readSubmittedAttempt(serverScoredBody({served: null}), UID), message);
});

test("percent without digits, or digits without percent, is refused by name", () => {
  // Percent, no codeAnswer key at all: the module's own rejection.
  expectRejection(
    () => readSubmittedAttempt(body({codeAnswer: undefined}), UID),
    REJECTION.CODE_ANSWER_REQUIRED_WITH_PERCENT(),
  );
  // A percent that is present but null claims nothing, so a body with neither is not "a percent
  // without digits" at all — it is a body with no digits, and its difficulty decides what happens.
  expectRejection(
    () => readSubmittedAttempt(body({codeAnswer: undefined, percentScore: null}), UID),
    REJECTION.EASY_WITHOUT_DIGITS(),
  );
  // An out-of-range percent is refused before the missing key is looked at, as today's order has it.
  expectRejection(() => readSubmittedAttempt(body({codeAnswer: undefined, percentScore: 101}), UID), "percentScore must be in 0..100");
  // Digits, no percent: today's own message, because today refuses this too.
  expectRejection(() => readSubmittedAttempt(body({percentScore: undefined}), UID), "percentScore must be in 0..100");
  expectRejection(() => readSubmittedAttempt(body({percentScore: undefined, difficulty: "HARD", served: SERVED_FOR_DIGITS}), UID), "percentScore must be in 0..100");
});

test("digits or a percent on a hard body with served still make it device-scored", () => {
  const withDigits = readSubmittedAttempt(body({difficulty: "HARD", served: SERVED_FOR_DIGITS}), UID);
  assert.strictEqual(withDigits.scoringAuthority, SCORING_AUTHORITY.CLIENT);
  assert.strictEqual(withDigits.scoreVerified, true);
  assert.deepStrictEqual(withDigits.served, SERVED_FOR_DIGITS);
});

/** Both kinds carry `served`; the same list must be judged the same way on each. */
const KINDS = [
  ["device-scored", (served, digits = "999") => body({codeAnswer: digits, percentScore: recomputePercentScore(digits), served})],
  ["server-scored", (served) => serverScoredBody({served})],
];

test("malformed served: not a list", () => {
  for (const [kind, make] of KINDS) {
    for (const notAList of [{}, "q0", 3, true, {length: 1, 0: {codeAnswerIndex: 0, questionId: "q0"}}]) {
      expectRejection(() => readSubmittedAttempt(make(notAList), UID), REJECTION.SERVED_NOT_A_LIST(), "invalid-argument");
    }
    assert.strictEqual(REJECTION.SERVED_NOT_A_LIST(), "served must be a list of {questionId, codeAnswerIndex}", kind);
  }
});

test("malformed served: an entry that is not an object, named by index", () => {
  const good = (at) => ({codeAnswerIndex: at, questionId: `q${at}`});
  for (const [, make] of KINDS) {
    for (const junk of [null, 3, "q1", [], undefined]) {
      expectRejection(
        () => readSubmittedAttempt(make([good(0), junk, good(2)]), UID),
        REJECTION.SERVED_ENTRY_NOT_AN_OBJECT(1),
      );
    }
  }
  assert.strictEqual(REJECTION.SERVED_ENTRY_NOT_AN_OBJECT(1), "served[1] must be an object");
});

test("malformed served: a position that is negative, fractional, missing, or not a number", () => {
  for (const [, make] of KINDS) {
    for (const position of [-1, 1.5, "1", null, undefined, NaN, Infinity, true]) {
      const entry = position === undefined ? {questionId: "q1"} : {codeAnswerIndex: position, questionId: "q1"};
      expectRejection(
        () => readSubmittedAttempt(make([{codeAnswerIndex: 0, questionId: "q0"}, entry]), UID),
        REJECTION.SERVED_POSITION_INVALID(1),
      );
    }
  }
  assert.strictEqual(REJECTION.SERVED_POSITION_INVALID(1), "served[1].codeAnswerIndex must be a non-negative integer");
});

test("malformed served: an id that is empty, missing, or not a string", () => {
  for (const [, make] of KINDS) {
    for (const questionId of ["", null, undefined, 7, {}, ["q1"]]) {
      const entry = questionId === undefined ? {codeAnswerIndex: 1} : {codeAnswerIndex: 1, questionId};
      expectRejection(
        () => readSubmittedAttempt(make([{codeAnswerIndex: 0, questionId: "q0"}, entry]), UID),
        REJECTION.SERVED_ID_INVALID(1),
      );
    }
  }
  assert.strictEqual(REJECTION.SERVED_ID_INVALID(1), "served[1].questionId must be a non-empty string");
});

test("malformed served: out of order", () => {
  const served = [{codeAnswerIndex: 2, questionId: "q2"}, {codeAnswerIndex: 0, questionId: "q0"}, {codeAnswerIndex: 1, questionId: "q1"}];
  for (const [, make] of KINDS) {
    expectRejection(() => readSubmittedAttempt(make(served), UID), REJECTION.SERVED_OUT_OF_ORDER(1, 0, 2));
  }
  assert.strictEqual(REJECTION.SERVED_OUT_OF_ORDER(1, 0, 2), "served[1] is out of order: codeAnswerIndex 0 after 2");
});

test("malformed served: a repeated position", () => {
  const served = [{codeAnswerIndex: 0, questionId: "q0"}, {codeAnswerIndex: 1, questionId: "q1"}, {codeAnswerIndex: 1, questionId: "q2"}];
  for (const [, make] of KINDS) {
    expectRejection(() => readSubmittedAttempt(make(served), UID), REJECTION.SERVED_POSITION_REPEATED(2, 1));
  }
  assert.strictEqual(REJECTION.SERVED_POSITION_REPEATED(2, 1), "served[2] repeats codeAnswerIndex 1");
});

test("malformed served: a repeated id, pointing back at its first appearance", () => {
  const served = [{codeAnswerIndex: 0, questionId: "q0"}, {codeAnswerIndex: 1, questionId: "q1"}, {codeAnswerIndex: 2, questionId: "q0"}];
  for (const [, make] of KINDS) {
    expectRejection(() => readSubmittedAttempt(make(served), UID), REJECTION.SERVED_ID_REPEATED(2, 0));
  }
  assert.strictEqual(REJECTION.SERVED_ID_REPEATED(2, 0), "served[2] repeats the questionId of served[0]");
});

test("malformed served: the first offence is the one named", () => {
  // Entry 1 has a bad id, entry 2 a bad position: entry 1 is named.
  const firstBad = [{codeAnswerIndex: 0, questionId: "q0"}, {codeAnswerIndex: 1, questionId: ""}, {codeAnswerIndex: -1, questionId: "q2"}];
  // One entry breaking two rules: its position is judged before its id.
  const bothWrong = [{codeAnswerIndex: 0, questionId: "q0"}, {codeAnswerIndex: "1", questionId: ""}];
  // Out of order and a duplicate id later: the order is caught first.
  const orderThenId = [{codeAnswerIndex: 1, questionId: "q1"}, {codeAnswerIndex: 0, questionId: "q0"}, {codeAnswerIndex: 2, questionId: "q1"}];
  for (const [, make] of KINDS) {
    expectRejection(() => readSubmittedAttempt(make(firstBad), UID), REJECTION.SERVED_ID_INVALID(1));
    expectRejection(() => readSubmittedAttempt(make(bothWrong), UID), REJECTION.SERVED_POSITION_INVALID(1));
    expectRejection(() => readSubmittedAttempt(make(orderThenId), UID), REJECTION.SERVED_OUT_OF_ORDER(1, 0, 1));
  }
});

test("malformed served: a sparse list is read hole by hole, not skipped past", () => {
  // `forEach` visits no holes, so `[<hole>, {...}]` would reach index 1 having pushed nothing and
  // read `codeAnswerIndex` off `served[0] === undefined` — a TypeError, surfacing to the client as
  // an internal error rather than as a named rejection. The hole is an entry, and it is not an
  // object. `Array(n)` and `delete` are the two ways a JSON-ish body can arrive holed.
  const holeFirst = [, {codeAnswerIndex: 1, questionId: "q1"}]; // eslint-disable-line no-sparse-arrays
  const holeLater = [{codeAnswerIndex: 0, questionId: "q0"}, , {codeAnswerIndex: 2, questionId: "q2"}]; // eslint-disable-line no-sparse-arrays
  const allHoles = new Array(3);
  const deleted = [{codeAnswerIndex: 0, questionId: "q0"}, {codeAnswerIndex: 1, questionId: "q1"}];
  delete deleted[0];
  for (const [, make] of KINDS) {
    expectRejection(() => readSubmittedAttempt(make(holeFirst), UID), REJECTION.SERVED_ENTRY_NOT_AN_OBJECT(0));
    expectRejection(() => readSubmittedAttempt(make(holeLater), UID), REJECTION.SERVED_ENTRY_NOT_AN_OBJECT(1));
    expectRejection(() => readSubmittedAttempt(make(allHoles), UID), REJECTION.SERVED_ENTRY_NOT_AN_OBJECT(0));
    expectRejection(() => readSubmittedAttempt(make(deleted), UID), REJECTION.SERVED_ENTRY_NOT_AN_OBJECT(0));
  }
});

test("bounded served: a position no pool could hold is refused before it reaches storage", () => {
  // `scoreAttempt` does refuse a position outside the pool — but it files that refusal as
  // SERVED_MALFORMED, and until this review that reason was a *server* fault, which under the
  // server-scored payment rule means nothing paid **and nothing charged**. So `codeAnswerIndex: 999`
  // bought an uncharged hard attempt logged as our own gap. It never gets that far now.
  assert.ok(Number.isInteger(1e300), "1e300 passes Number.isInteger — the bound is what stops it");
  for (const [kind, make] of KINDS) {
    for (const position of [MAX_SERVED_POSITION + 1, 999_999, 1e300, Number.MAX_SAFE_INTEGER]) {
      if (position <= MAX_SERVED_POSITION) continue;
      expectRejection(
        () => readSubmittedAttempt(make([{codeAnswerIndex: position, questionId: "q0"}]), UID),
        REJECTION.SERVED_POSITION_TOO_LARGE(0, position),
        "invalid-argument",
      );
    }
    // The last position that is still accepted, so the bound is off by nothing.
    const atTheEdge = readSubmittedAttempt(
      serverScoredBody({served: [{codeAnswerIndex: MAX_SERVED_POSITION, questionId: "q0"}]}),
      UID,
    );
    assert.deepStrictEqual(atTheEdge.served, [{codeAnswerIndex: MAX_SERVED_POSITION, questionId: "q0"}], kind);
  }
  assert.strictEqual(
    REJECTION.SERVED_POSITION_TOO_LARGE(0, 999_999),
    `served[0].codeAnswerIndex is 999999; the last position accepted is ${MAX_SERVED_POSITION}`,
  );
});

test("bounded served: a list, an id and a codeAnswer too large to store are refused by name", () => {
  // A hundred thousand entries validate, are accepted, and then fail the Firestore 1 MiB write
  // *inside* the transaction that carries up to fifty attempts — one crafted body takes the whole
  // batch down with it. The same for one entry holding a megabyte of question id.
  const entry = (at) => ({codeAnswerIndex: at, questionId: `q${at}`});
  const tooMany = Array.from({length: MAX_SERVED_ENTRIES + 1}, (unused, at) => entry(at));
  for (const [, make] of KINDS) {
    expectRejection(() => readSubmittedAttempt(make(tooMany), UID), REJECTION.SERVED_TOO_LONG(tooMany.length));
    expectRejection(
      () => readSubmittedAttempt(make([{codeAnswerIndex: 0, questionId: "q".repeat(MAX_QUESTION_ID_CHARS + 1)}]), UID),
      REJECTION.SERVED_ID_TOO_LONG(0),
    );
  }
  // The largest list that is still accepted, entry for entry.
  const atTheEdge = Array.from({length: MAX_SERVED_ENTRIES}, (unused, at) => entry(at));
  const accepted = readSubmittedAttempt(serverScoredBody({served: atTheEdge}), UID);
  assert.strictEqual(accepted.served.length, MAX_SERVED_ENTRIES);
  assert.strictEqual(
    readSubmittedAttempt(serverScoredBody({served: [{codeAnswerIndex: 0, questionId: "q".repeat(MAX_QUESTION_ID_CHARS)}]}), UID)
      .served[0].questionId.length,
    MAX_QUESTION_ID_CHARS,
  );

  // And the digits themselves: today accepts a codeAnswer of any length at all.
  const huge = "9".repeat(MAX_CODE_ANSWER_CHARS + 1);
  withFixedClock(() => {
    const legacy = normalizeLessonResultAttemptEvent(body({codeAnswer: huge, percentScore: 100}), UID);
    assert.strictEqual(legacy.codeAnswer.length, huge.length, "today stores it whole");
  });
  expectRejection(
    () => readSubmittedAttempt(body({codeAnswer: huge, percentScore: 100}), UID),
    REJECTION.CODE_ANSWER_TOO_LONG(huge.length),
  );
  // Today's own rejection still comes first: a long body full of letters is refused as letters.
  expectRejection(
    () => readSubmittedAttempt(body({codeAnswer: `${huge}a`, percentScore: 100}), UID),
    "codeAnswer must contain digits only",
  );
  const longest = "9".repeat(MAX_CODE_ANSWER_CHARS);
  assert.strictEqual(
    readSubmittedAttempt(body({codeAnswer: longest, percentScore: 100}), UID).codeAnswer.length,
    MAX_CODE_ANSWER_CHARS,
  );
});

test("crafted percent: accepted, scoreVerified false, exactly today's behaviour", () => {
  const attempt = readSubmittedAttempt(body({percentScore: 99}), UID);
  assert.strictEqual(attempt.scoringAuthority, SCORING_AUTHORITY.CLIENT);
  assert.strictEqual(attempt.percentScore, 99, "the claim is kept for analysis");
  assert.strictEqual(attempt.expectedPercentScore, PERCENT);
  assert.strictEqual(attempt.scoreVerified, false);
  assert.strictEqual(attempt.paymentRule, PAYMENT_RULE.DEVICE_SCORED);
  assert.strictEqual(isPayable(attempt), false, "pay nothing");
});

// ═══ 3. The payment rule ════════════════════════════════════════════════════════════════════════

test("payment rule, device-scored: both of the device's claims about itself, and nothing else", () => {
  const honest = readSubmittedAttempt(body({served: SERVED_FOR_DIGITS}), UID);
  assert.strictEqual(honest.paymentRule, PAYMENT_RULE.DEVICE_SCORED);
  assert.strictEqual(isPayable(honest), true);
  assert.strictEqual(isPayable(honest, {scorable: false}), true, "a scoring result is not read for this kind");
  const craftedPercent = readSubmittedAttempt(body({percentScore: 1}), UID);
  assert.strictEqual(isPayable(craftedPercent), false);
  assert.strictEqual(
    isPayable(craftedPercent, {scorable: true, unscorable: []}),
    false,
    "no scoring result can rescue a crafted claim",
  );
  // The other claim, the same consequence: one lie, one policy.
  const craftedServed = readSubmittedAttempt(body({served: SERVED_FOR_DIGITS.slice(0, 2)}), UID);
  assert.strictEqual(craftedServed.scoreVerified, true);
  assert.strictEqual(craftedServed.servedVerified, false);
  assert.strictEqual(isPayable(craftedServed), false);
  // And it takes both: a hand-made attempt missing either flag is not payable.
  assert.strictEqual(isPayable({paymentRule: PAYMENT_RULE.DEVICE_SCORED, scoreVerified: true}), false);
  assert.strictEqual(isPayable({paymentRule: PAYMENT_RULE.DEVICE_SCORED, servedVerified: true}), false);
  assert.strictEqual(
    isPayable({paymentRule: PAYMENT_RULE.DEVICE_SCORED, scoreVerified: true, servedVerified: true}),
    true,
  );
});

test("payment rule, server-scored: only after scoring, and only with no server-fault unscorables", () => {
  const attempt = readSubmittedAttempt(serverScoredBody(), UID);
  assert.strictEqual(attempt.paymentRule, PAYMENT_RULE.SERVER_SCORED);
  const clientFault = {questionId: "q1", codeAnswerIndex: 1, reason: UNSCORABLE.MALFORMED_ANSWER, fault: FAULT.CLIENT, detail: null};
  const serverFault = {questionId: "q2", codeAnswerIndex: 2, reason: UNSCORABLE.KEY_MISSING, fault: FAULT.SERVER, detail: null};
  assert.strictEqual(isPayable(attempt), false, "not scored yet");
  assert.strictEqual(isPayable(attempt, null), false);
  assert.strictEqual(isPayable(attempt, {scorable: false, codeAnswer: null, percentScore: null, unscorable: [], omitted: 0}), false);
  assert.strictEqual(isPayable(attempt, {scorable: true, codeAnswer: "999", percentScore: 100, unscorable: [], omitted: 0}), true);
  assert.strictEqual(isPayable(attempt, {scorable: true, codeAnswer: "919", percentScore: 66, unscorable: [clientFault], omitted: 0}), true, "a client fault is a '1', not a reason to withhold");
  assert.strictEqual(isPayable(attempt, {scorable: true, codeAnswer: "911", percentScore: 33, unscorable: [clientFault, serverFault], omitted: 0}), false, "a server fault is our gap; nothing is paid on it");
  assert.strictEqual(isPayable(attempt, {scorable: "true", unscorable: []}), false, "scorable must be the boolean, not something truthy");
});

test("payment rule: an attempt of no known kind is not payable", () => {
  assert.strictEqual(isPayable({paymentRule: "something-else", scoreVerified: true}, {scorable: true, unscorable: []}), false);
  assert.strictEqual(isPayable({}, {scorable: true, unscorable: []}), false);
});

test("payment rule: isPayable fails closed on anything it cannot inspect", () => {
  const scored = {scorable: true, codeAnswer: "999", percentScore: 100, unscorable: [], omitted: 0};
  // No attempt at all: `attempt.paymentRule` used to be a TypeError, which a caller in a try block
  // would have read as "the batch failed" rather than "this one is not payable".
  for (const attempt of [null, undefined, "attempt", 7, []]) {
    assert.strictEqual(isPayable(attempt, scored), false, String(attempt));
  }
  // An `unscorable` that is not a list cannot be searched for server faults, and "no server faults
  // were found in it" is exactly the claim that must not be made. Treating it as [] paid on it.
  const attempt = readSubmittedAttempt(serverScoredBody(), UID);
  for (const unscorable of [undefined, null, "none", {length: 0}, 0, {}]) {
    assert.strictEqual(
      isPayable(attempt, {scorable: true, codeAnswer: "999", percentScore: 100, unscorable}),
      false,
      JSON.stringify(unscorable),
    );
  }
  for (const scoring of ["scored", 7, true, []]) {
    assert.strictEqual(isPayable(attempt, scoring), false, JSON.stringify(scoring));
  }
});

test("withServerScore hands the wiring step a finished attempt, not two nulls and a rule", () => {
  const attempt = withFixedClock(() => readSubmittedAttempt(serverScoredBody(), UID));
  assert.strictEqual(attempt.codeAnswer, null, "this is what the wiring step would otherwise store");

  const scored = {scorable: true, codeAnswer: "9110", percentScore: 44, unscorable: [], omitted: 0};
  const filled = withServerScore(attempt, scored);
  assert.strictEqual(filled.codeAnswer, "9110");
  assert.strictEqual(filled.percentScore, 44);
  assert.strictEqual(filled.payable, true);
  assert.deepStrictEqual(
    Object.keys(filled),
    [...Object.keys(attempt), "payable"],
    "the fields keep their order; only `payable` is new",
  );
  // What the consumers would have done with the raw attempt, and what they do with this one.
  assert.deepStrictEqual(attemptActivityCounts(attempt.codeAnswer), {questions: 4, correct: 0},
    "four questions read out of the string \"null\"");
  assert.deepStrictEqual(attemptActivityCounts(filled.codeAnswer), {questions: 3, correct: 1});

  // An attempt the server could not score keeps no number in either direction, and is not payable.
  const serverFault = {questionId: "q1", codeAnswerIndex: 1, reason: UNSCORABLE.KEY_MISSING, fault: FAULT.SERVER, detail: null};
  for (const scoring of [
    null,
    undefined,
    {scorable: false, codeAnswer: null, percentScore: null, unscorable: [serverFault], omitted: 0},
    {scorable: true, codeAnswer: null, percentScore: null, unscorable: []},
    {scorable: true, codeAnswer: "999", percentScore: "lots", unscorable: []},
  ]) {
    const refused = withServerScore(attempt, scoring);
    assert.strictEqual(typeof refused.codeAnswer, "string", JSON.stringify(scoring));
    assert.strictEqual(typeof refused.percentScore, "number", JSON.stringify(scoring));
    assert.deepStrictEqual(attemptActivityCounts(refused.codeAnswer), {questions: 0, correct: 0});
  }
  assert.strictEqual(withServerScore(attempt, null).payable, false);
  assert.strictEqual(withServerScore(attempt, {scorable: true, codeAnswer: "9", percentScore: 100, unscorable: [serverFault]}).payable, false);

  // A device-scored attempt passes through with its own digits untouched and the flag decided.
  const device = withServerScore(readSubmittedAttempt(body(), UID), null);
  assert.strictEqual(device.codeAnswer, DIGITS);
  assert.strictEqual(device.percentScore, PERCENT);
  assert.strictEqual(device.payable, true);
  assert.strictEqual(withServerScore(readSubmittedAttempt(body({percentScore: 99}), UID), null).payable, false);

  // Nothing in, nothing out.
  for (const nothing of [null, undefined, "attempt", 7]) {
    assert.strictEqual(withServerScore(nothing, scored), null, String(nothing));
  }
});

test("the served this module returns is the shape scoreAttempt takes, and the rule holds against its real output", () => {
  const pool = [0, 1, 2].map((at) => ({id: `q${at}`, lessonId: "lesson-1", difficulty: "", payload: JSON.stringify(SINGLE)}));
  const served = [0, 1, 2].map((at) => ({codeAnswerIndex: at, questionId: `q${at}`}));
  const data = serverScoredBody({
    served,
    answers: [
      {questionId: "q0", codeAnswerIndex: 0, score: 9, answerPayload: "{\"type\":\"single-choice\",\"selected\":\"b\"}", answeredAtMs: FIXED_NOW - 5000, durationMs: 100, wasTimeout: false},
      {questionId: "q1", codeAnswerIndex: 1, score: 9, answerPayload: "{", answeredAtMs: FIXED_NOW - 4000, durationMs: 100, wasTimeout: false},
    ],
  });
  const attempt = readSubmittedAttempt(data, UID);

  // Scored: one right, one unparseable (a client fault, '1'), one never answered ('1').
  const scored = scoreAttempt({questions: pool, served: attempt.served, keyDocument: null, answers: attempt.answers});
  assert.strictEqual(scored.scorable, true);
  assert.strictEqual(scored.codeAnswer, "911");
  assert.strictEqual(scored.percentScore, 33);
  assert.deepStrictEqual(scored.unscorable.map((record) => record.fault), [FAULT.CLIENT]);
  assert.strictEqual(isPayable(attempt, scored), true);

  // The same attempt against a pool of the same size that no longer holds q1. The question `served`
  // names is not filed where the lesson says it is — recorded by name, scored as shown-and-
  // unanswered, and still charged. Refusing here would have cost nothing, which is what made a
  // made-up served entry worth sending.
  const renamed = pool.map((question) => (question.id === "q1" ? {...question, id: "q-elsewhere"} : question));
  const gap = scoreAttempt({questions: renamed, served: attempt.served, keyDocument: null, answers: attempt.answers});
  assert.strictEqual(gap.scorable, true);
  assert.deepStrictEqual(gap.unscorable.map((record) => record.reason), [UNSCORABLE.QUESTION_MISSING]);
  assert.ok(gap.unscorable.every((record) => record.fault === FAULT.CLIENT));
  assert.strictEqual(gap.codeAnswer, "911");
  assert.strictEqual(isPayable(attempt, gap), true);

  // A gap that really is ours still stops the payment: an unreadable stored payload is nothing the
  // device can have caused, and nothing is paid or charged on it.
  const unreadable = pool.map((question) => (question.id === "q1" ? {...question, payload: "{"} : question));
  const ours = scoreAttempt({questions: unreadable, served: attempt.served, keyDocument: null, answers: attempt.answers});
  assert.strictEqual(ours.scorable, false);
  assert.ok(ours.unscorable.some((record) => record.fault === FAULT.SERVER));
  assert.strictEqual(isPayable(attempt, ours), false);

  // And served absent on the device-scored kind is what scoreAttempt calls unknown — the wiring step
  // must never hand a null through as if it were a list.
  const legacyClient = readSubmittedAttempt(body(), UID);
  assert.strictEqual(legacyClient.served, null);
  const unknown = scoreAttempt({questions: pool, served: legacyClient.served, keyDocument: null, answers: legacyClient.answers});
  assert.strictEqual(unknown.scorable, false);
  assert.deepStrictEqual(unknown.unscorable.map((record) => record.reason), [UNSCORABLE.SERVED_UNKNOWN]);
});

// ═══ Constants ══════════════════════════════════════════════════════════════════════════════════

/**
 * Representative arguments for each rejection, so the messages compared below are the messages
 * clients will actually read.
 *
 * They used to be called with `(1, 2, 3)` across the board, which handed the served-versus-digits
 * rejection the number `2` where it expected a codeAnswer and read `.length` off it — `undefined`,
 * silently, inside the very assertion meant to prove the messages are distinct and well-formed. The
 * `undefined` check below is what stops that happening to a rejection added later.
 */
const REJECTION_ARGUMENTS = {
  CODE_ANSWER_REQUIRED_WITH_PERCENT: [],
  CODE_ANSWER_TOO_LONG: [MAX_CODE_ANSWER_CHARS + 1],
  EASY_WITHOUT_DIGITS: [],
  HARD_WITHOUT_SERVED: [],
  DIFFICULTY_NOT_A_STRING: [],
  SERVED_NOT_A_LIST: [],
  SERVED_TOO_LONG: [MAX_SERVED_ENTRIES + 1],
  SERVED_ENTRY_NOT_AN_OBJECT: [1],
  SERVED_POSITION_INVALID: [1],
  SERVED_POSITION_TOO_LARGE: [1, MAX_SERVED_POSITION + 1],
  SERVED_ID_INVALID: [1],
  SERVED_ID_TOO_LONG: [1],
  SERVED_OUT_OF_ORDER: [2, 1, 3],
  SERVED_POSITION_REPEATED: [2, 1],
  SERVED_ID_REPEATED: [2, 0],
};

test("the constants the wiring step compares against", () => {
  assert.deepStrictEqual(SCORING_AUTHORITY, {CLIENT: "client", SERVER: "server"});
  assert.deepStrictEqual(PAYMENT_RULE, {DEVICE_SCORED: "device-scored", SERVER_SCORED: "server-scored"});
  assert.deepStrictEqual(
    Object.keys(REJECTION).sort(),
    Object.keys(REJECTION_ARGUMENTS).sort(),
    "a rejection was added or removed without representative arguments to render it with",
  );
  const messages = Object.entries(REJECTION).map(([name, make]) => [name, make(...REJECTION_ARGUMENTS[name])]);
  for (const [name, message] of messages) {
    assert.strictEqual(typeof message, "string", `${name} did not render a string`);
    assert.ok(message.length > 0, `${name} rendered an empty message`);
    assert.ok(!message.includes("undefined"), `${name} rendered "undefined" into its message: ${message}`);
    assert.ok(!TODAYS_REJECTIONS.includes(message), `a new rejection reuses today's message: ${message}`);
  }
  const rendered = messages.map(([, message]) => message);
  assert.strictEqual(new Set(rendered).size, rendered.length, "two rejections share a message");
});

test("the bounds are stated once and agree with each other", () => {
  // A dense codeAnswer of the maximum length has one served entry per position, and its last
  // position is the largest one accepted. A bound that disagreed with its neighbour would refuse a
  // body the other two call legal.
  assert.strictEqual(MAX_SERVED_ENTRIES, MAX_CODE_ANSWER_CHARS);
  assert.strictEqual(MAX_SERVED_POSITION, MAX_CODE_ANSWER_CHARS - 1);
  for (const bound of [MAX_CODE_ANSWER_CHARS, MAX_SERVED_ENTRIES, MAX_SERVED_POSITION, MAX_QUESTION_ID_CHARS]) {
    assert.ok(Number.isInteger(bound) && bound > 0, `${bound} is not a usable bound`);
  }
  // Generous against anything the app builds: a lesson's eligible pool is tens of questions.
  assert.ok(MAX_CODE_ANSWER_CHARS >= 500, "the bound must not refuse a large but real lesson");
});

// ─── Run ────────────────────────────────────────────────────────────────────────────────────────

let failures = 0;
for (const [name, fn] of SUITE) {
  try {
    fn();
  } catch (error) {
    failures += 1;
    console.error(`FAIL ${name}\n  ${error && error.stack ? error.stack : error}`);
  }
}
if (failures > 0) {
  console.error(`attempt-intake.test.js: ${failures} of ${SUITE.length} cases failed`);
  process.exitCode = 1;
} else {
  console.log(`attempt-intake.test.js OK (${SUITE.length} cases)`);
}
