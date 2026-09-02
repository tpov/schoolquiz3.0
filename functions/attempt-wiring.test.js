"use strict";

const assert = require("assert");
const fs = require("fs");
const path = require("path");

const {isStorableDocumentId, keyDocumentPath} = require("./question-key-store");
const {buildScoringPool} = require("./scoring-pool");
const {FAULT, UNSCORABLE, scoreAttempt} = require("./attempt-scoring");
const {noClaims, settleClaims, validateClaimMask} = require("./charge-claims");
const {
  PAYMENT_RULE,
  SCORING_AUTHORITY,
  readSubmittedAttempt,
  withServerScore,
} = require("./attempt-intake");
const {AT, ANSWERS, publishLesson, servedQuestions} = require("./_scored-lesson-fixture");

/**
 * The decisions `applyLessonResultEvents` makes about one submitted attempt, driven as it makes them.
 *
 * `attempt-handler.test.js` runs the whole callable over a store and is what proves these are wired
 * together at all; this suite is the table of cases underneath it — every branch of "who scored
 * this, what was it worth, may it be paid for", which are cheap here and expensive there.
 *
 * `index.js` cannot be required from a plain node test without a stand-in for `firebase-admin`, and
 * these functions do not need one: they are pure, they are declared at top level, and their
 * **source text is cut out of `index.js` at test time** and evaluated in a scope holding the real
 * modules and a recording logger. The two that read get a stub `db` in the same scope. So every
 * case below fails when `index.js` changes, not when a copy of it does.
 *
 * What this suite deliberately cannot do is check that anything *calls* these functions: delete the
 * line that scores every attempt and every case here stays green. That is the other suite's job.
 */

const UID = "player-1";
const LESSON = "lesson-wiring";

// ─── Cutting the handler's own functions out of index.js ────────────────────────────────────────

const INDEX_SOURCE = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");

/**
 * The text of a top-level `function name(...) { ... }`, from `function` to its closing brace.
 *
 * Same rule as `attempt-intake.test.js`: every function looked up here is declared at column 0 with
 * its body indented, so the first `}` at column 0 after the header closes it. `async` is accepted
 * because several of these read.
 */
function extractFunction(source, name) {
  const start = ["\nfunction ", "\nasync function "]
    .map((keyword) => source.indexOf(`${keyword}${name}(`))
    .filter((at) => at !== -1)
    .sort((left, right) => left - right)[0];
  assert.notStrictEqual(start, undefined, `function ${name} is not declared at top level in index.js`);
  const end = source.indexOf("\n}\n", start);
  assert.notStrictEqual(end, -1, `function ${name} never closes at column 0`);
  return source.slice(start + 1, end + 2);
}

/** A top-level `const NAME = …;`, taken rather than restated so its value is pinned too. */
function extractConst(source, name) {
  const start = source.indexOf(`\nconst ${name} = `);
  assert.notStrictEqual(start, -1, `const ${name} is not declared at top level in index.js`);
  const end = source.indexOf("\n};\n", start);
  const line = source.indexOf(";\n", start);
  // An object or array literal closes at column 0; a scalar ends on its own line.
  const stop = end !== -1 && end < line ? end + "\n};".length : line + 1;
  return `${source.slice(start + 1, stop)}\n`;
}

/** The functions under test, in dependency order. */
const WIRED = [
  "unreadLesson",
  "questionDocumentFor",
  "readLessonKeyDocument",
  "readScopedLessonQuestions",
  "readLessonScoringSource",
  "readLessonScoringSources",
  "readStoredAttemptScores",
  "storedScoreFields",
  "serverScoringFor",
  "reportServerScoredAttempt",
  "reportDeviceScoredAttempt",
  "applyServerScoring",
  "storedAttemptFields",
];

/** The constants they read, cut out too rather than restated. */
const WIRED_CONSTANTS = ["LOGGED_SCORING_SAMPLE", "LESSON_UNREAD", "STORED_ATTEMPT_FIELDS"];

/**
 * The helpers they close over, taken from `index.js` as well.
 *
 * `lessonContextKey` above all: it is the whole of "two lessons with one id in different scopes
 * must not share an entry", and a copy written here would agree with itself while the handler keyed
 * its map some other way.
 */
const WIRED_SUPPORT = [
  "stringValue",
  "numberValue",
  "normalizeScope",
  "isSingleDocumentId",
  "lessonContextKey",
  "privateCatalogPath",
  "privateQuestPath",
  "privateSectionPath",
  "privateThemePath",
  "privateLessonPath",
];

/**
 * The handler's decision code, evaluated against the real modules and a logger that records.
 *
 * The dependencies are passed by name rather than required inside, because they are what the
 * functions close over in `index.js`: swapping one for a stand-in here would test the stand-in.
 * Every one is the module `index.js` itself requires — except `db` and `logger`, the two things a
 * test has to stand in for.
 */
function loadWiring(db) {
  const warnings = [];
  const logger = {
    warn: (message, payload) => warnings.push({level: "warn", message, payload}),
    error: (message, payload) => warnings.push({level: "error", message, payload}),
  };
  const body = [
    "\"use strict\";",
    "const PUBLIC_SCOPE = \"public\";",
    "const PRIVATE_SCOPE = \"private\";",
    ...WIRED_CONSTANTS.map((name) => extractConst(INDEX_SOURCE, name)),
    ...WIRED_SUPPORT.map((name) => extractFunction(INDEX_SOURCE, name)),
    ...WIRED.map((name) => extractFunction(INDEX_SOURCE, name)),
    `return {${[...WIRED, ...WIRED_CONSTANTS, "lessonContextKey"].join(", ")}};`,
  ].join("\n");
  // eslint-disable-next-line no-new-func
  const wired = new Function(
    "PAYMENT_RULE",
    "SCORING_AUTHORITY",
    "buildScoringPool",
    "scoreAttempt",
    "withServerScore",
    "noClaims",
    "FAULT",
    "logger",
    "isStorableDocumentId",
    "keyDocumentPath",
    "db",
    body,
  )(
    PAYMENT_RULE,
    SCORING_AUTHORITY,
    buildScoringPool,
    scoreAttempt,
    withServerScore,
    noClaims,
    FAULT,
    logger,
    isStorableDocumentId,
    keyDocumentPath,
    db === undefined ? null : db,
  );
  return {...wired, warnings};
}

const SUITE = [];
const test = (name, fn) => SUITE.push([name, fn]);

// ─── The lesson ─────────────────────────────────────────────────────────────────────────────────

/** 9, 4, 5, 5, 9 — (100 + 37 + 50 + 50 + 100) / 5, truncated. See `lesson-round-trip.test.js`. */
const CODE_ANSWER = "94559";
const PERCENT = 67;

const PUBLISHED = publishLesson(LESSON);
const SERVED = servedQuestions(LESSON);

/** What `readLessonScoringSources` hands back, built here instead of read from Firestore. */
function sourcesFor(entries) {
  return new Map(entries.map(({key, keyDocument, documents, failure}) => [
    key,
    {
      keyDocument: keyDocument === undefined ? null : keyDocument,
      documents: documents || [],
      failure: failure === undefined ? null : failure,
    },
  ]));
}

// ─── The bodies ─────────────────────────────────────────────────────────────────────────────────

function baseBody(overrides) {
  const body = {
    userId: UID,
    attemptId: "attempt-1",
    catalogId: "catalog-1",
    questId: "quest-1",
    sectionId: "section-1",
    themeId: "theme-1",
    lessonId: LESSON,
    lessonVersion: 1,
    sourceShelf: "arena",
    difficulty: "HARD",
    answers: ANSWERS,
    completedAtMs: 1_700_000_005_000,
    createdAtMs: 1_700_000_000_000,
    ...(overrides || {}),
  };
  for (const key of Object.keys(body)) if (body[key] === undefined) delete body[key];
  return body;
}

/** A hard attempt with no digits and a served list: the server is asked to score it. */
const serverScoredBody = (overrides) => baseBody({served: SERVED, ...(overrides || {})});

/**
 * Today's body: the device's own digits and percent, and the served list beside them.
 *
 * The list is not optional decoration. `CompleteAttemptUseCase` and `AbortAttemptUseCase` both pass
 * `playOrder.toServedQuestions()` and `LessonResultOutboxWriter` writes it into the queued body, so
 * every attempt reaching the server through the queue carries one — which is what makes
 * `servedVerified` a payment condition that is live on the next release rather than a future one.
 */
const deviceScoredBody = (overrides) =>
  baseBody({codeAnswer: CODE_ANSWER, percentScore: PERCENT, served: SERVED, ...(overrides || {})});

/** One entry of `events` as `applyLessonResultEvents` builds it, before scoring. */
function item(body) {
  return {event: readSubmittedAttempt(body, UID), content: {}, ref: {}};
}

/** The sources map for one lesson, keyed exactly as the handler keys it. */
function sourcesForLesson(body, source) {
  const wiring = loadWiring();
  return sourcesFor([{key: wiring.lessonContextKey(readSubmittedAttempt(body, UID)), ...source}]);
}

/** Runs the handler's scoring step over a batch, and hands back the items and the log. */
function decide(bodies, sources, replays) {
  const wiring = loadWiring();
  const events = bodies.map(item);
  const built = sources === undefined ? sourcesForLesson(bodies[0], PUBLISHED) : sources;
  wiring.applyServerScoring(events, built, replays);
  return {events, wiring, warnings: wiring.warnings, sources: built};
}

// ═══ 1. A device-scored attempt ═════════════════════════════════════════════════════════════════

/**
 * Every field `result_events` holds, in the order a document carries them.
 *
 * Written down rather than read out of `index.js`: this is the schema, and a list computed from the
 * code under test would agree with whatever that code happens to produce. Everything the pre-swap
 * intake returned is here — that part is the promise that a device-scored attempt is stored as it
 * always was — plus the two the wiring adds on purpose. `served` is the only authority on which
 * question sat at which position, and without it a score the server charged real currency for
 * cannot be re-derived or disputed; `scoringAuthority` is what tells a server-scored row from a row
 * written before `scoreVerified` existed, which is otherwise only the *absence* of two fields.
 */
const STORED_FIELDS = [
  "answers",
  "userId",
  "scope",
  "ownerUid",
  "catalogId",
  "questId",
  "sectionId",
  "themeId",
  "lessonId",
  "lessonVersion",
  "sourceShelf",
  "attemptId",
  "difficulty",
  "codeAnswer",
  "chargeClaims",
  "percentScore",
  "expectedPercentScore",
  "scoreVerified",
  "completedAtMs",
  "createdAtMs",
  "served",
  "scoringAuthority",
];

/** What the handler decides by and the collection does not hold. */
const DECISION_FIELDS = ["servedVerified", "paymentRule", "payable"];

test("a device-scored attempt keeps every field it was stored with, with the same values", () => {
  const {events, wiring} = decide([deviceScoredBody()]);
  const stored = wiring.storedAttemptFields(events[0].event);

  assert.deepStrictEqual(Object.keys(stored), STORED_FIELDS);
  assert.strictEqual(stored.codeAnswer, CODE_ANSWER);
  assert.strictEqual(stored.percentScore, PERCENT);
  assert.strictEqual(stored.expectedPercentScore, PERCENT);
  assert.strictEqual(stored.scoreVerified, true);
  assert.strictEqual(stored.difficulty, "HARD");
  assert.strictEqual(stored.chargeClaims, noClaims(CODE_ANSWER.length));
  assert.deepStrictEqual(stored.served, SERVED);
  assert.strictEqual(stored.scoringAuthority, SCORING_AUTHORITY.CLIENT);

  const asRead = readSubmittedAttempt(deviceScoredBody(), UID);
  for (const field of STORED_FIELDS) {
    assert.deepStrictEqual(stored[field], asRead[field], `${field} changed value`);
  }
  for (const field of DECISION_FIELDS) {
    assert.ok(!(field in stored), `${field} reached the stored document`);
  }
});

test("the stored fields are an allowlist, so a field grown later cannot leak into storage", () => {
  // A denylist stores whatever the intake or this handler grows next, by default and for ever.
  const {wiring} = decide([deviceScoredBody()]);
  const invented = wiring.storedAttemptFields({
    ...readSubmittedAttempt(deviceScoredBody(), UID),
    someFieldAddedLater: "should not be stored",
    internalScratchpad: {big: "object"},
  });
  assert.deepStrictEqual(Object.keys(invented), STORED_FIELDS);
  assert.deepStrictEqual(wiring.STORED_ATTEMPT_FIELDS.slice(0, STORED_FIELDS.length), STORED_FIELDS);
});

test("a device-scored attempt is paid for on exactly the flag that used to gate it", () => {
  const honest = decide([deviceScoredBody()]).events[0];
  assert.strictEqual(honest.event.paymentRule, PAYMENT_RULE.DEVICE_SCORED);
  assert.strictEqual(honest.payable, true, "an honest attempt stopped being paid for");

  const liar = decide([deviceScoredBody({percentScore: 100})]).events[0];
  assert.strictEqual(liar.event.scoreVerified, false);
  assert.strictEqual(liar.payable, false);
  assert.strictEqual(liar.event.percentScore, 100, "the claim was rewritten instead of kept");
  assert.strictEqual(liar.event.expectedPercentScore, PERCENT);
});

test("a served list that disagrees with the digits pays nothing, and says so where someone looks", () => {
  // The condition this slice made live. Today's client sends `served` through the queue, so a
  // client bug that misbuilds the list would stop paying every player who has it — silently, with
  // nothing anywhere saying why, because the attempt is stored looking perfectly verified.
  const {events, warnings} = decide([deviceScoredBody({served: SERVED.slice(0, 4)})]);

  assert.strictEqual(events[0].event.scoreVerified, true, "the percent still follows from the digits");
  assert.strictEqual(events[0].event.servedVerified, false);
  assert.strictEqual(events[0].payable, false);
  assert.strictEqual(events[0].event.codeAnswer, CODE_ANSWER, "the event was not kept intact");
  assert.strictEqual(warnings.length, 1);
  assert.match(warnings[0].message, /served list/);
  assert.strictEqual(warnings[0].payload.attemptId, "attempt-1");
  assert.strictEqual(warnings[0].payload.servedCount, 4);
  assert.strictEqual(warnings[0].payload.codeAnswerLength, CODE_ANSWER.length);
});

test("a crafted percent is refused today's way, and logged no more than it was before", () => {
  // The other device-scored refusal is not new and was never logged; starting to log it would put
  // a line under every crafted body in the corpus and drown the one above, which is new.
  const {warnings} = decide([deviceScoredBody({percentScore: 100})]);
  assert.deepStrictEqual(warnings, []);
});

test("a batch of today's bodies is decided without any lesson source at all", () => {
  const {events, warnings} = decide(
    [deviceScoredBody(), deviceScoredBody({attemptId: "attempt-2", percentScore: 3})],
    new Map(),
  );
  assert.strictEqual(events[0].payable, true);
  assert.strictEqual(events[1].payable, false);
  assert.deepStrictEqual(warnings, []);
});

// ═══ 2. A server-scored attempt ═════════════════════════════════════════════════════════════════

test("a hard attempt with no digits is scored from the stored keys and stored like any other", () => {
  const {events, warnings, wiring} = decide([serverScoredBody()]);
  const {event, payable} = events[0];
  const stored = wiring.storedAttemptFields(event);

  assert.strictEqual(event.scoringAuthority, SCORING_AUTHORITY.SERVER);
  assert.strictEqual(payable, true);
  assert.strictEqual(event.codeAnswer, CODE_ANSWER, "the server's digits are not the lesson's");
  assert.strictEqual(event.percentScore, PERCENT);
  assert.deepStrictEqual(warnings, [], "a clean run was logged as a failure");

  // The same fields a device-scored attempt is stored with, minus the two that exist only to check
  // a claim this device did not make: there is no percent of its own to have followed from its own
  // digits. Everything the transaction reads afterwards is present and means the same thing.
  assert.deepStrictEqual(
    Object.keys(stored).slice().sort(),
    STORED_FIELDS.filter((f) => f !== "expectedPercentScore" && f !== "scoreVerified").sort(),
  );
  for (const field of DECISION_FIELDS) {
    assert.ok(!(field in stored), `${field} reached the stored document`);
  }
});

test("a server-scored attempt carries a charge mask settlement can actually read", () => {
  // `settleClaims` runs on every new attempt, inside the transaction, and throws when the mask and
  // the digits are different lengths — which would take the whole batch, not just this attempt.
  const {event} = decide([serverScoredBody()]).events[0];

  assert.strictEqual(event.chargeClaims, noClaims(CODE_ANSWER.length));
  assert.strictEqual(validateClaimMask(event.chargeClaims, event.codeAnswer, event.difficulty), null);
  const settled = settleClaims(event.chargeClaims, event.codeAnswer, 5, 5, []);
  assert.strictEqual(settled.codeAnswer, CODE_ANSWER, "an empty mask changed the score");
  assert.strictEqual(settled.standardChargesPaid, 0);
  assert.strictEqual(settled.plasmaChargesPaid, 0);
});

test("a lesson whose keys were never stored pays nothing, and says why", () => {
  const noKeys = sourcesForLesson(serverScoredBody(), {
    keyDocument: null,
    documents: PUBLISHED.documents,
  });
  const {events, warnings} = decide([serverScoredBody()], noKeys);

  assert.strictEqual(events[0].payable, false);
  assert.strictEqual(events[0].event.codeAnswer, "", "an unscorable attempt was given digits");
  assert.strictEqual(events[0].event.percentScore, 0);
  assert.strictEqual(warnings.length, 1);
  assert.strictEqual(warnings[0].payload.attemptId, "attempt-1");
  assert.strictEqual(warnings[0].payload.payable, false);
  // A redacted payload with no key filed for it — the server's own gap, which is why it pays
  // nothing. The reasons are filtered to those, since a client's fault is ordinary play.
  assert.deepStrictEqual(warnings[0].payload.serverFaultReasons, [UNSCORABLE.KEY_MISSING]);
  assert.strictEqual(warnings[0].payload.serverFaultCount, 4);
  assert.strictEqual(warnings[0].payload.omitted, 0);
});

test("a lesson whose questions all vanished is our gap, not the player's", () => {
  // The bug this pairing exists to stop. Every served position becomes a filler, every digit a
  // '1', and the attempt used to score: payable, a percent of zero, and the player charged full
  // price for a lesson the server had failed to read. `LESSON_MISSING` is now a server fault.
  const empty = sourcesForLesson(serverScoredBody(), {
    keyDocument: PUBLISHED.keyDocument,
    documents: [],
  });
  const {events, warnings} = decide([serverScoredBody()], empty);

  assert.strictEqual(events[0].payable, false, "the player was charged for a lesson we lost");
  assert.strictEqual(events[0].event.codeAnswer, "");
  assert.strictEqual(events[0].event.percentScore, 0);
  assert.strictEqual(warnings.length, 1);
  assert.deepStrictEqual(warnings[0].payload.serverFaultReasons, [UNSCORABLE.LESSON_MISSING]);
  assert.strictEqual(warnings[0].payload.missingCount, SERVED.length);
});

test("one question lost out of five still costs its own position, and still pays", () => {
  // The boundary. A device can invent a served entry; it cannot empty a lesson, so "some missing"
  // keeps the treatment that is safe against the invented one — and the loss is still logged,
  // through `pool.missing`, on an attempt that was paid for.
  const partial = sourcesForLesson(serverScoredBody(), {
    keyDocument: PUBLISHED.keyDocument,
    documents: PUBLISHED.documents.filter((document) => document.id !== "q-order"),
  });
  const {events, warnings} = decide([serverScoredBody()], partial);

  assert.strictEqual(events[0].payable, true);
  assert.strictEqual(events[0].event.codeAnswer, "94159");
  assert.strictEqual(warnings.length, 1, "a paid attempt hid the question it lost");
  assert.strictEqual(warnings[0].payload.payable, true);
  assert.strictEqual(warnings[0].payload.missingCount, 1);
  assert.deepStrictEqual(warnings[0].payload.missingQuestions, [
    {questionId: "q-order", codeAnswerIndex: AT.order},
  ]);
  assert.strictEqual(warnings[0].payload.unscorableCount, 1);
  // That record is the client's, so it is not among the reasons that make an attempt unpayable.
  assert.deepStrictEqual(warnings[0].payload.serverFaultReasons, []);
});

test("a lesson that was never read is not a lesson that is empty", () => {
  // The two used to be one state: a missing source became `documents: []`, which scores exactly
  // like a lesson whose questions were all deleted. A read bug must never be able to present
  // itself as the player's fault, so an unread lesson has a name and is refused under it.
  const cases = [
    ["never read at all", new Map(), "lesson-not-read"],
    ["the read failed", sourcesForLesson(serverScoredBody(), {failure: "lesson-read-failed"}), "lesson-read-failed"],
    ["no usable path", sourcesForLesson(serverScoredBody(), {failure: "unusable-lesson-path"}), "unusable-lesson-path"],
  ];
  for (const [name, sources, expected] of cases) {
    const {events, warnings, wiring} = decide([serverScoredBody()], sources);
    assert.strictEqual(events[0].payable, false, name);
    assert.strictEqual(events[0].event.codeAnswer, "", name);
    assert.strictEqual(events[0].event.percentScore, 0, name);
    assert.strictEqual(warnings.length, 1, name);
    assert.strictEqual(warnings[0].payload.unread, expected, name);
    assert.strictEqual(warnings[0].payload.poolReason, null, name);
    assert.ok(Object.values(wiring.LESSON_UNREAD).includes(expected), name);
  }
});

test("an attempt whose served list cannot be placed in a pool pays nothing", () => {
  const duplicated = sourcesForLesson(serverScoredBody(), {
    keyDocument: PUBLISHED.keyDocument,
    documents: [...PUBLISHED.documents, PUBLISHED.documents[0]],
  });
  const {events, warnings} = decide([serverScoredBody()], duplicated);

  assert.strictEqual(events[0].payable, false);
  assert.strictEqual(events[0].event.codeAnswer, "");
  assert.strictEqual(warnings.length, 1);
  assert.strictEqual(warnings[0].payload.unread, null);
  assert.strictEqual(warnings[0].payload.poolReason, "duplicate-document");
  assert.strictEqual(warnings[0].payload.poolDetail, "q-single");
});

test("the client's own score is never read on the server-scored path", () => {
  // Every answer row claims `score: 9`. Three of the five digits are not 9, so the claim was not
  // read — which is the whole reason the handler asks the scorer at all.
  assert.strictEqual(decide([serverScoredBody()]).events[0].event.codeAnswer, CODE_ANSWER);
  assert.notStrictEqual(CODE_ANSWER, "99999");
});

// ═══ 3. A replay is not re-scored ═══════════════════════════════════════════════════════════════

test("a redelivered attempt keeps the score it was paid on", () => {
  // The offline queue redelivers. Re-scoring the second delivery reads today's questions and keys,
  // and a question republished in between moves the digits — so the stored percent would fall
  // while `lessonBest` and the tournament row kept the original. One attempt, two scores.
  const wiring = loadWiring();
  const events = [item(serverScoredBody())];
  const replays = new Map([[events[0], {codeAnswer: "99999", percentScore: 100}]]);
  wiring.applyServerScoring(events, sourcesForLesson(serverScoredBody(), PUBLISHED), replays);

  assert.strictEqual(events[0].event.codeAnswer, "99999", "the replay was re-scored over its score");
  assert.strictEqual(events[0].event.percentScore, 100);
  assert.strictEqual(events[0].payable, false, "a replay was offered payment");
  assert.strictEqual(events[0].event.chargeClaims, noClaims(5));
  assert.deepStrictEqual(wiring.warnings, [], "a replay was reported as a scoring failure");
});

test("the stored score is read as a score, or not at all", () => {
  const {wiring} = decide([serverScoredBody()]);
  const snapshotOf = (data) => ({exists: data !== null, data: () => data});

  assert.deepStrictEqual(
    wiring.storedScoreFields(snapshotOf({codeAnswer: "941", percentScore: 55})),
    {codeAnswer: "941", percentScore: 55},
  );
  // Nothing on file, or nothing that is a score: `{}`, so spreading it over the document being
  // written changes nothing. A `codeAnswer: ""` invented here would erase the one already stored.
  assert.deepStrictEqual(wiring.storedScoreFields(snapshotOf(null)), {});
  assert.deepStrictEqual(wiring.storedScoreFields(snapshotOf({})), {});
  assert.deepStrictEqual(wiring.storedScoreFields(snapshotOf({codeAnswer: 941})), {});
  assert.deepStrictEqual(wiring.storedScoreFields(undefined), {});
  // Digits but no percent still answer, at the percent an absent field means.
  assert.deepStrictEqual(
    wiring.storedScoreFields(snapshotOf({codeAnswer: "0"})),
    {codeAnswer: "0", percentScore: 0},
  );
});

// ═══ 4. A batch decides each attempt on its own ═════════════════════════════════════════════════

test("fifty attempts of both kinds are each decided on their own", () => {
  const OTHER = "lesson-nobody-can-read";
  const bodies = [];
  for (let index = 0; index < 48; index += 1) {
    const attemptId = `attempt-${index}`;
    if (index % 3 === 0) bodies.push(deviceScoredBody({attemptId}));
    else if (index % 3 === 1) bodies.push(deviceScoredBody({attemptId, percentScore: 100}));
    else bodies.push(serverScoredBody({attemptId}));
  }
  // The one nobody can score, in the middle of the run rather than at the end.
  bodies.splice(20, 0, serverScoredBody({attemptId: "attempt-unreadable", lessonId: OTHER}));
  bodies.push(serverScoredBody({attemptId: "attempt-last"}));
  assert.strictEqual(bodies.length, 50, "the batch is not the size the callable accepts");

  const wiring = loadWiring();
  const sources = sourcesFor([
    {key: wiring.lessonContextKey(readSubmittedAttempt(serverScoredBody(), UID)), ...PUBLISHED},
    {
      key: wiring.lessonContextKey(readSubmittedAttempt(serverScoredBody({lessonId: OTHER}), UID)),
      failure: "lesson-read-failed",
    },
  ]);
  const events = bodies.map(item);
  wiring.applyServerScoring(events, sources, new Map());

  const unreadable = events.find((entry) => entry.event.attemptId === "attempt-unreadable");
  assert.strictEqual(unreadable.payable, false);
  assert.strictEqual(unreadable.event.codeAnswer, "");

  for (const entry of events) {
    if (entry.event.attemptId === "attempt-unreadable") continue;
    if (entry.event.scoringAuthority === SCORING_AUTHORITY.SERVER) {
      assert.strictEqual(entry.payable, true, `${entry.event.attemptId} was dragged down`);
      assert.strictEqual(entry.event.codeAnswer, CODE_ANSWER, entry.event.attemptId);
      assert.strictEqual(entry.event.percentScore, PERCENT, entry.event.attemptId);
      continue;
    }
    const honest = entry.event.percentScore === PERCENT;
    assert.strictEqual(entry.payable, honest, `${entry.event.attemptId} decided by its neighbours`);
    assert.strictEqual(entry.event.codeAnswer, CODE_ANSWER, entry.event.attemptId);
  }

  // One line, for the one attempt that earned one.
  assert.strictEqual(wiring.warnings.length, 1);
  assert.strictEqual(wiring.warnings[0].payload.attemptId, "attempt-unreadable");
});

test("scoring one attempt leaves the bodies and the lesson sources it read untouched", () => {
  const before = JSON.stringify({SERVED, ANSWERS, documents: PUBLISHED.documents});
  decide([serverScoredBody(), deviceScoredBody({attemptId: "attempt-2"})]);
  assert.strictEqual(JSON.stringify({SERVED, ANSWERS, documents: PUBLISHED.documents}), before);
});

// ═══ 5. The reads ═══════════════════════════════════════════════════════════════════════════════

/**
 * Firestore, cut down to the calls these functions make, recording every one.
 *
 * A stub rather than an emulator: these cases are about *which* reads the handler issues, how many,
 * and what it does when one fails — properties of the calls, not of what a database returns.
 */
function stubDb(lessons, failures) {
  const reads = [];
  const fail = (path) => {
    if (failures && failures[path]) throw new Error(failures[path]);
  };
  const docsOf = (key) => (lessons.has(key) ? lessons.get(key).documents || [] : []);
  return {
    reads,
    doc: (docPath) => ({
      get: async () => {
        reads.push(`doc ${docPath}`);
        fail(docPath);
        const lessonId = docPath.slice(docPath.indexOf("/") + 1);
        const keyDocument = lessons.has(lessonId) ? lessons.get(lessonId).keyDocument : null;
        return {
          exists: keyDocument !== null && keyDocument !== undefined,
          data: () => keyDocument,
        };
      },
    }),
    collection: (name) => ({
      get: async () => {
        reads.push(`collection ${name}`);
        fail(name);
        return {docs: docsOf(name).map((document) => ({id: document.id, data: () => document}))};
      },
    }),
  };
}

/** The key document is filed under the lesson id, so that is what the stub keys it by. */
const STUB_LESSONS = new Map([[LESSON, {
  keyDocument: PUBLISHED.keyDocument,
  documents: PUBLISHED.documents.map((document) => ({...document})),
}]]);

/** The questions map `readLessonQuestions` builds for the whole batch, keyed by bare lessonId. */
const PUBLIC_QUESTIONS = new Map([[LESSON, PUBLISHED.documents.map((document) => ({...document}))]]);

test("a batch of today's bodies reads nothing at all", async () => {
  const db = stubDb(STUB_LESSONS);
  const wiring = loadWiring(db);
  const sources = await wiring.readLessonScoringSources(
    [deviceScoredBody(), deviceScoredBody()].map(item),
    PUBLIC_QUESTIONS,
  );
  assert.deepStrictEqual(db.reads, []);
  assert.strictEqual(sources.size, 0);
});

test("fifty attempts on one public lesson cost one key document and no second question query", async () => {
  // The questions were already read once for the whole batch, for the reward. Reading them again
  // here ran the same query twice, five lines apart.
  const db = stubDb(STUB_LESSONS);
  const wiring = loadWiring(db);
  const bodies = [];
  for (let index = 0; index < 50; index += 1) {
    bodies.push(index % 2 === 0 ?
      serverScoredBody({attemptId: `attempt-${index}`}) :
      deviceScoredBody({attemptId: `attempt-${index}`}));
  }
  const sources = await wiring.readLessonScoringSources(bodies.map(item), PUBLIC_QUESTIONS);

  assert.deepStrictEqual(db.reads, [`doc ${PUBLISHED.keyDocumentPath}`]);
  assert.strictEqual(sources.size, 1);
  const source = [...sources.values()][0];
  assert.deepStrictEqual(source.keyDocument, PUBLISHED.keyDocument);
  assert.strictEqual(source.failure, null);
  assert.deepStrictEqual(source.documents, PUBLIC_QUESTIONS.get(LESSON));

  // And what it hands back is what the scoring step then reads.
  const events = bodies.map(item);
  wiring.applyServerScoring(events, sources, new Map());
  assert.strictEqual(events[0].event.codeAnswer, CODE_ANSWER);
  assert.strictEqual(events[0].payable, true);
});

test("a private lesson is read from its owner's tree, not from the public collection", async () => {
  // Only the public publish path writes `questions/{id}`. A private quest's questions live under
  // `private/{uid}/…/lessons/{id}/questions`, so reading a private lesson from the public
  // collection found nothing, every served question was missing, and the player was charged.
  // The owner is the player: a private event whose `ownerUid` is anyone else is refused at intake.
  const privatePath = `private/${UID}/catalogs/catalog-1/quests/quest-1/sections/section-1` +
    `/themes/theme-1/lessons/${LESSON}/questions`;
  const lessons = new Map([
    [privatePath, {documents: PUBLISHED.documents}],
    [LESSON, {keyDocument: PUBLISHED.keyDocument}],
  ]);
  const db = stubDb(lessons);
  const wiring = loadWiring(db);
  const body = serverScoredBody({scope: "private", ownerUid: UID});
  const sources = await wiring.readLessonScoringSources([item(body)], new Map());

  assert.ok(db.reads.includes(`collection ${privatePath}`), `read ${JSON.stringify(db.reads)}`);
  assert.strictEqual(sources.size, 1);
  const source = [...sources.values()][0];
  assert.strictEqual(source.failure, null);
  assert.strictEqual(source.documents.length, PUBLISHED.documents.length);

  const events = [item(body)];
  wiring.applyServerScoring(events, sources, new Map());
  assert.strictEqual(events[0].event.codeAnswer, CODE_ANSWER, "a private lesson did not score");
  assert.strictEqual(events[0].payable, true);
});

test("one lesson id in two scopes is two entries, not one", async () => {
  // The key is scope-qualified for a reason: `lessonId` comes off the device, and a public and a
  // private lesson may both be called `lesson-wiring`. Keyed by the bare id, whichever was read
  // first would answer for both — one player's questions scoring another player's attempt.
  const db = stubDb(new Map());
  const wiring = loadWiring(db);
  const publicBody = serverScoredBody({attemptId: "a-public"});
  const privateBody = serverScoredBody({attemptId: "a-private", scope: "private", ownerUid: UID});
  const sources = await wiring.readLessonScoringSources(
    [publicBody, privateBody].map(item),
    PUBLIC_QUESTIONS,
  );

  assert.strictEqual(sources.size, 2, "two scopes shared one lesson entry");
  const publicKey = wiring.lessonContextKey(readSubmittedAttempt(publicBody, UID));
  const privateKey = wiring.lessonContextKey(readSubmittedAttempt(privateBody, UID));
  assert.notStrictEqual(publicKey, privateKey);
  assert.strictEqual(sources.get(publicKey).documents.length, PUBLISHED.documents.length);
  assert.strictEqual(sources.get(privateKey).documents.length, 0);
});

test("a lesson id Firestore would not take as a document id is never handed to db.doc", async () => {
  const db = stubDb(new Map());
  const wiring = loadWiring(db);
  const bodies = [
    serverScoredBody({attemptId: "a", lessonId: "lessons/nested"}),
    serverScoredBody({attemptId: "b", lessonId: "__reserved__"}),
  ];
  const sources = await wiring.readLessonScoringSources(bodies.map(item), new Map());

  assert.deepStrictEqual(
    db.reads.filter((read) => read.startsWith("doc ")),
    [],
    "a document id Firestore rejects was handed to db.doc",
  );
  // Its questions were not in the batch's map either, so the lesson is unread — refused, not
  // scored against nothing.
  assert.strictEqual(sources.size, 2);
  for (const source of sources.values()) {
    assert.strictEqual(source.keyDocument, null);
    assert.strictEqual(source.failure, "unusable-lesson-path");
  }
});

test("one lesson whose read fails costs its own attempt and no other", async () => {
  // A rejection, not a synchronous throw: a permission error or a deadline on one key document
  // used to reject out of the whole handler and take the other forty-nine attempts with it.
  const db = stubDb(STUB_LESSONS, {[PUBLISHED.keyDocumentPath]: "7 PERMISSION_DENIED"});
  const wiring = loadWiring(db);
  const sources = await wiring.readLessonScoringSources(
    [serverScoredBody()].map(item),
    PUBLIC_QUESTIONS,
  );

  assert.strictEqual(sources.size, 1);
  const source = [...sources.values()][0];
  assert.strictEqual(source.failure, "lesson-read-failed");
  assert.deepStrictEqual(source.documents, [], "an unread lesson kept documents");
  assert.strictEqual(wiring.warnings.length, 1);
  assert.strictEqual(wiring.warnings[0].level, "error");
  assert.match(wiring.warnings[0].payload.message, /PERMISSION_DENIED/);

  // And the batch survives it: the failure is one attempt's, not the call's.
  const events = [item(serverScoredBody()), item(deviceScoredBody({attemptId: "attempt-2"}))];
  wiring.applyServerScoring(events, sources, new Map());
  assert.strictEqual(events[0].payable, false);
  assert.strictEqual(events[1].payable, true, "a device-scored attempt was lost to another's read");
});

test("only a server-scored attempt is checked for having been stored before", async () => {
  const seen = [];
  const refFor = (attemptId, exists) => ({
    path: attemptId,
    get: async () => {
      seen.push(attemptId);
      return {exists, data: () => (exists ? {codeAnswer: "941", percentScore: 55} : undefined)};
    },
  });
  const wiring = loadWiring();
  const events = [
    {...item(serverScoredBody({attemptId: "server-new"})), ref: refFor("server-new", false)},
    {...item(serverScoredBody({attemptId: "server-replay"})), ref: refFor("server-replay", true)},
    {...item(deviceScoredBody({attemptId: "device"})), ref: refFor("device", true)},
  ];
  const replays = await wiring.readStoredAttemptScores(events);

  assert.deepStrictEqual(seen.slice().sort(), ["server-new", "server-replay"]);
  assert.strictEqual(replays.size, 1);
  assert.deepStrictEqual(replays.get(events[1]), {codeAnswer: "941", percentScore: 55});
});

test("not being able to tell whether an attempt was stored is not the same as it being new", async () => {
  // Advisory: a failure here means the attempt is scored, exactly as it was before this read
  // existed, and the transaction's own snapshot still refuses to charge or overwrite twice.
  const wiring = loadWiring();
  const events = [{
    ...item(serverScoredBody()),
    ref: {
      path: "x",
      get: async () => {
        throw new Error("4 DEADLINE_EXCEEDED");
      },
    },
  }];
  const replays = await wiring.readStoredAttemptScores(events);

  assert.strictEqual(replays.size, 0);
  assert.strictEqual(wiring.warnings.length, 1);
  assert.match(wiring.warnings[0].payload.message, /DEADLINE_EXCEEDED/);
});

// ────────────────────────────────────────────────────────────────────────────────────────────────

/** Awaited: several cases drive reads, and a rejected promise nobody waits on is a silent pass. */
async function run() {
  let failures = 0;
  for (const [name, fn] of SUITE) {
    try {
      await fn();
    } catch (error) {
      failures += 1;
      console.error(`FAIL ${name}`);
      console.error(`  ${error.stack}`);
    }
  }
  if (failures > 0) {
    console.error(`attempt-wiring.test.js: ${failures} of ${SUITE.length} cases failed`);
    process.exitCode = 1;
  } else {
    console.log(`attempt-wiring.test.js OK (${SUITE.length} cases)`);
  }
}

run();
