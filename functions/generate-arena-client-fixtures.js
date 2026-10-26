"use strict";

/**
 * Regenerates the two arena client fixture files from the CURRENT producers, so the
 * `sourceSha256` pins in `FirebaseArenaTicketRemoteDataSourceTest` and
 * `FirebaseArenaLessonCatalogRemoteDataSourceTest` mean what their comments claim.
 *
 * The 2026-09-07 capture tooling stayed session-local and was never committed; every later
 * producer change was absorbed by hand-editing recorded SHAs, which is exactly the drift the
 * fixtures exist to catch (epic-3 retro L9). This script is the committed capture procedure:
 *
 *   - the recorded worlds are rebuilt from the fixtures' own content (uids, request ids, lesson
 *     versions, ratings, placements, the frozen capture clocks);
 *   - the real production entry points drive them (`index.js` callables over
 *     `_firestore-stub.js`, the real `createArenaLessonCatalog` with its captured
 *     `loadLessonContext`);
 *   - every section is regenerated and compared against the recorded file, section by section.
 *
 * Default mode reports drift without writing anything. `node generate-arena-client-fixtures.js
 * --write` rewrites the two fixture JSONs in place. Run it from this directory (it resolves
 * `firebase-admin` from `functions/node_modules` like every other suite here).
 *
 * Determinism: wall-clock time is frozen per phase (the recorded capture instants), random
 * selection cannot escape the world (only `lesson` carries questions, so the HMAC-ordered
 * chooser always lands on it), and no generated section contains a live timestamp.
 */

const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

const REPO = path.resolve(__dirname, "..");
const FIXTURE_DIR = path.join(REPO, "shared/feature/lesson-runner/data/src/jvmTest/resources");
const TICKET_FIXTURE = path.join(FIXTURE_DIR, "arena-ticket-client-fixtures.json");
const CATALOG_FIXTURE = path.join(FIXTURE_DIR, "arena-catalog-client-fixtures.json");

/** The two capture instants recorded in the 2026-09-07 fixtures. */
const TICKET_NOW = 1_788_696_000_000; // 2026-09-06T12:00:00Z — tickets (issuedAt/dayId)
const CATALOG_NOW = 1_788_739_200_000; // 2026-09-07T00:00:00Z — catalog quotes (serverNowMs)
const PLACEMENT_AT = 1_788_739_199_999; // recorded placement updatedAtMs (1ms before the quote)

const sha256 = (value) => crypto.createHash("sha256").update(value).digest("hex");

async function writeSnapshot(refs) {
  const rows = [];
  for (const ref of refs) {
    const snapshot = await ref.get();
    if (snapshot.exists) rows.push([ref.path, snapshot.data()]);
  }
  return rows;
}

async function main() {
  const doWrite = process.argv.includes("--write");
  const recordedTickets = JSON.parse(fs.readFileSync(TICKET_FIXTURE, "utf8"));
  const recordedCatalog = JSON.parse(fs.readFileSync(CATALOG_FIXTURE, "utf8"));

  const {createFirestoreStub} = require("./_firestore-stub");
  const questionFixtures = require("./_question-fixtures");
  const {STATUS: REDACTION} = require("./question-redaction");
  const realCreateCatalog = require("./arena-lesson-catalog").createArenaLessonCatalog;

  // Capture the production root loader exactly the way verify-demo-fixtures.js does: wrap the
  // catalog factory before index.js loads, take the loader it was wired with, put the original
  // back.
  let loadArenaLessonContext = null;
  require("./arena-lesson-catalog").createArenaLessonCatalog = (dependencies) => {
    loadArenaLessonContext = dependencies.loadLessonContext;
    return realCreateCatalog(dependencies);
  };
  const {admin, store} = createFirestoreStub({});
  const adminPath = require.resolve("firebase-admin");
  const adminStub = new (require("module"))(adminPath, null);
  adminStub.filename = adminPath;
  adminStub.loaded = true;
  adminStub.exports = admin;
  require.cache[adminPath] = adminStub;
  const functions = require("./index");
  require("./arena-lesson-catalog").createArenaLessonCatalog = realCreateCatalog;
  if (typeof loadArenaLessonContext !== "function") throw new Error("root loader was not captured");

  const realNow = Date.now;
  const call = (name, data, owner = "player") => functions[name].run({auth: {uid: owner}, data});

  // ---------------------------------------------------------------------------
  // Ticket world: the recorded "lesson" plus eleven helpers that lift the pricing
  // universe to twelve distinct levels, putting the lesson on the second level so its
  // selected price is the recorded 10 nolics (1 + round(99 * 1 / 11)).
  // ---------------------------------------------------------------------------
  const helperDocs = {};
  for (let index = 0; index < 11; index += 1) {
    const id = `aux-${String(index + 1).padStart(2, "0")}`;
    // Ratings are 1..3 stars, so count <= sum <= 3*count must hold. Twenty 1..3-star votes
    // lift the universe to twelve distinct weighted-score levels with the lesson on the
    // second-lowest: its selected price comes out at the recorded 10 nolics.
    const ratingSum = index === 0 ? 21 : 22 + index; // 21, 23, 24, ... 32 — the lesson's own sum is 22
    Object.assign(helperDocs, {
      [`catalogs/catalog-${id}`]: {questType: "REGULAR"},
      [`quests/quest-${id}`]: {catalogId: `catalog-${id}`, authorUid: "author", visibleOn: ["archive"]},
      [`sections/section-${id}`]: {questId: `quest-${id}`},
      [`themes/theme-${id}`]: {sectionId: `section-${id}`},
      [`lessons/${id}`]: {themeId: `theme-${id}`, version: 1, title: `Aux ${index + 1}`,
        reviewSubmissionId: `submission-${id}`, reviewApprovedAtMs: 1},
      [`lesson_placements/${id}`]: {lessonId: id, shelf: "arena", authorUid: "author",
        contentVersion: 1, previousShelf: "review", placementVersion: 1, updatedAtMs: PLACEMENT_AT},
      [`lesson_ratings/${id}`]: {ratingSum, ratingCount: 20, ratingVersion: 1},
      [`admin/review/lessons/${id}`]: {ownerUid: "author", questId: `quest-${id}`,
        submissionId: `submission-${id}`, status: "PUBLISHED", sourceLanguages: ["en"]},
      ...Object.fromEntries(["TESTING", "LOGIC"].map((kind) => [`admin/review/lessons/${id}/reviews/${kind}`, {
        kind, reviewerUid: `${kind.toLowerCase()}-${id}`, acceptedByServer: true, score: 3,
        reviewerLevelAtSubmit: 100, submissionId: `submission-${id}`,
      }])),
    });
  }
  const questionPayload = JSON.stringify({...questionFixtures.SINGLE, id: "q1", difficulty: "EASY"});
  const ticketWorld = {
    "users/player": {uid: "player", standardHearts: 10, goldHearts: 3, lifePoints: 1000,
      plasmaPoints: 300, lifePointsUpdatedAtMs: TICKET_NOW, plasmaPointsUpdatedAtMs: TICKET_NOW,
      nolics: 1000, pointsNolics: 1000},
    "users/author": {nolics: 0, pointsNolics: 0},
    "profiles/player": {uid: "player"},
    "catalogs/catalog": {questType: "REGULAR"},
    "quests/quest": {catalogId: "catalog", authorUid: "author", visibleOn: ["archive"]},
    "sections/section": {questId: "quest"},
    "themes/theme": {sectionId: "section"},
    "lessons/lesson": {themeId: "theme", version: 3, title: "Lesson",
      reviewSubmissionId: "submission", reviewApprovedAtMs: TICKET_NOW - 1000},
    "lesson_placements/lesson": {lessonId: "lesson", shelf: "arena", authorUid: "author",
      contentVersion: 3, previousShelf: "review", placementVersion: 1, updatedAtMs: PLACEMENT_AT},
    "lesson_ratings/lesson": {ratingSum: 22, ratingCount: 20, ratingVersion: 5},
    "admin/review/lessons/lesson": {ownerUid: "author", questId: "quest", submissionId: "submission",
      status: "PUBLISHED", sourceLanguages: ["en"]},
    "admin/review/lessons/lesson/reviews/tester": {kind: "TESTING", reviewerUid: "tester",
      acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100, submissionId: "submission"},
    "admin/review/lessons/lesson/reviews/logic": {kind: "LOGIC", reviewerUid: "reviewer",
      acceptedByServer: true, score: 3, reviewerLevelAtSubmit: 100, submissionId: "submission"},
    "questions/q1": {lessonId: "lesson", version: 2, difficulty: "EASY", language: "en",
      payload: questionPayload},
    ...helperDocs,
  };

  Date.now = () => TICKET_NOW;
  store.reset(ticketWorld);
  const tickets = {};
  // The current producer filters the lesson bank by the document's difficulty, so one q1
  // document cannot serve both tickets the way the 2026-09-07 capture world did. The EASY
  // tickets are issued first, the question document is then swapped to its HARD variant and
  // the hard ticket is issued from it — every recorded section is still a genuine producer
  // output, and the frozen tickets never re-read the live bank.
  tickets.issuedSelected = await call("prepareArenaEntry", {
    requestId: "client-selected", mode: "SELECTED", lessonId: "lesson", difficulty: "EASY",
  });
  tickets.issuedRandom = await call("prepareArenaEntry", {
    requestId: "client-random", mode: "RANDOM", difficulty: "EASY",
  });
  store.documents.set("questions/q1", {lessonId: "lesson", version: 2, difficulty: "HARD",
    language: "en", payload: JSON.stringify({...questionFixtures.SINGLE, id: "q1", difficulty: "HARD"})});
  tickets.issuedHard = await call("prepareArenaEntry", {
    requestId: "client-hard", mode: "SELECTED", lessonId: "lesson", difficulty: "HARD",
  });

  const selectedTicket = tickets.issuedSelected.ticket;
  const hardTicket = tickets.issuedHard.ticket;
  const listPageOne = await call("listArenaEntryTickets", {pageSize: 2});
  tickets.listFirst = listPageOne;
  tickets.listLast = await call("listArenaEntryTickets", {
    pageSize: 2, afterTicketId: listPageOne.nextTicketId,
  });
  tickets.recoveredSelected = await call("fetchArenaEntryTicket", {ticketId: selectedTicket.ticketId});

  const settlePayload = (ticket) => ({
    ticketId: ticket.ticketId, attemptId: ticket.attemptId, ownerUid: "player",
    lessonId: ticket.lessonId, lessonVersion: ticket.lessonVersion, difficulty: ticket.difficulty,
    completionKind: "COMPLETED",
    answers: ticket.questions.map((question, codeAnswerIndex) => ({
      questionId: question.questionId, codeAnswerIndex,
      answerPayload: JSON.stringify({type: "single-choice", selected: "b"}),
      answeredAtMs: TICKET_NOW, durationMs: 1,
    })),
  });
  await call("submitLessonResultEvents", {attempts: [settlePayload(hardTicket)]});

  tickets.cancelled = await call("cancelArenaEntry", {
    ticketId: selectedTicket.ticketId, requestId: "cancel-client-selected",
  });
  tickets.recoveredCancelled = await call("fetchArenaEntryTicket", {ticketId: selectedTicket.ticketId});
  tickets.issuedCancelledReplay = await call("prepareArenaEntry", {
    requestId: "client-selected", mode: "SELECTED", lessonId: "lesson", difficulty: "EASY",
  });
  tickets.recoveredSettled = await call("fetchArenaEntryTicket", {ticketId: hardTicket.ticketId});
  tickets.cancelSettled = await call("cancelArenaEntry", {
    ticketId: hardTicket.ticketId, requestId: "cancel-client-hard",
  });
  Date.now = realNow;

  // The HARD public formats and the unsafe already-redacted producer outputs ride the
  // question-redaction producer directly, over the shared production question payloads.
  const redactCases = [
    ["SINGLE", questionFixtures.SINGLE],
    ["MULTIPLE", questionFixtures.MULTIPLE],
    ["ORDERING", questionFixtures.ORDERING],
    ["FILL_BLANK", questionFixtures.FILL_BLANK],
    ["SURVEY", questionFixtures.SURVEY],
  ];
  tickets.hardPublicPayloads = redactCases.map(([name, source]) => {
    const full = {...source, id: "q1", difficulty: "HARD"};
    const split = redactionStatus(JSON.stringify(full));
    // Surveys are participation-scored and are never redacted: their frozen HARD payload is
    // the full source content, exactly what loadEntry freezes for them.
    const payload = split.status === REDACTION.REDACTED ? split.publicPayload :
      split.status === REDACTION.NOT_APPLICABLE && full.type === "Survey" ? JSON.stringify(full) :
        null;
    if (payload === null) {
      throw new Error(`expected ${name} to redact or pass through for HARD, got ${split.status}`);
    }
    return {name, source: full, payload};
  });
  const recordedUnsafe = recordedTickets.unsafeAlreadyRedactedPayloads;
  tickets.unsafeAlreadyRedactedPayloads = recordedUnsafe.map((recorded) => {
    const base = JSON.parse(
      tickets.hardPublicPayloads.find((row) => row.name === recorded.name).payload,
    );
    const leaked = JSON.parse(recorded.payload);
    base[recorded.field] = leaked[recorded.field];
    const payload = JSON.stringify(base);
    const split = redactionStatus(payload);
    const producerStatus = split.status === REDACTION.ALREADY_REDACTED ? "already-redacted" :
      split.status === REDACTION.NOT_APPLICABLE ? "not-applicable" : null;
    if (producerStatus !== recorded.producerStatus) {
      throw new Error(`${recorded.name}/${recorded.field}: producer status drifted ` +
        `${recorded.producerStatus} -> ${producerStatus}`);
    }
    return {name: recorded.name, field: recorded.field, payload, producerStatus};
  });

  // ---------------------------------------------------------------------------
  // "Unchanged actual runtime world": modern source documents carry no embedded IDs; the
  // production normalizer must make the frozen wire usable without touching the sources.
  // ---------------------------------------------------------------------------
  const armSourcePayload = JSON.stringify({...questionFixtures.SINGLE, difficulty: "EASY"});
  const armWorld = {
    ...ticketWorld,
    "lessons/lesson": {...ticketWorld["lessons/lesson"], version: 1},
    "lesson_placements/lesson": {...ticketWorld["lesson_placements/lesson"], contentVersion: 1},
    "lesson_ratings/lesson": {ratingSum: 0, ratingCount: 0, ratingVersion: 0},
    "questions/EASY-0": {lessonId: "lesson", version: 1, difficulty: "EASY", payload: armSourcePayload},
    "questions/EASY-1": {lessonId: "lesson", version: 1, difficulty: "EASY", payload: armSourcePayload},
  };
  delete armWorld["questions/q1"];
  Date.now = () => TICKET_NOW;
  store.reset(armWorld);
  const armRequestId = recordedTickets.actualRuntimeMissingId.issued.ticket.requestId;
  const armIssued = await call("prepareArenaEntry", {
    requestId: armRequestId, mode: "SELECTED", lessonId: "lesson", difficulty: "EASY",
  });
  const armSourcesBefore = await writeSnapshot([
    store.db.doc("questions/EASY-0"), store.db.doc("questions/EASY-1"),
  ]);
  const armTicket = armIssued.ticket;
  const armSettled = await call("submitLessonResultEvents", {attempts: [settlePayload(armTicket)]});
  const armSettleResponse = armSettled.results
    ? armSettled.results[0]
    : armSettled;
  const armSourcesAfter = await writeSnapshot([
    store.db.doc("questions/EASY-0"), store.db.doc("questions/EASY-1"),
  ]);
  Date.now = realNow;
  tickets.actualRuntimeMissingId = {
    sourceQuestions: armSourcesBefore,
    issued: armIssued,
    settled: armSettleResponse,
    sourceQuestionsUnchanged: JSON.stringify(armSourcesAfter) === JSON.stringify(armSourcesBefore),
    sourceHadEmbeddedIds: JSON.parse(armSourcePayload).id !== undefined,
  };
  if (!tickets.actualRuntimeMissingId.sourceQuestionsUnchanged) {
    throw new Error("issue mutated the source question documents");
  }

  const ticketFixtures = {
    schemaVersion: 1,
    producer: recordedTickets.producer,
    sourceSha256: sourceHashes(recordedTickets.sourceSha256),
    hashes: ticketHashes(),
    issuedSelected: tickets.issuedSelected,
    issuedHard: tickets.issuedHard,
    issuedRandom: tickets.issuedRandom,
    recoveredSelected: tickets.recoveredSelected,
    listFirst: tickets.listFirst,
    listLast: tickets.listLast,
    cancelled: tickets.cancelled,
    recoveredCancelled: tickets.recoveredCancelled,
    issuedCancelledReplay: tickets.issuedCancelledReplay,
    recoveredSettled: tickets.recoveredSettled,
    cancelSettled: tickets.cancelSettled,
    hardPublicPayloads: tickets.hardPublicPayloads,
    unsafeAlreadyRedactedPayloads: tickets.unsafeAlreadyRedactedPayloads,
    actualRuntimeMissingId: tickets.actualRuntimeMissingId,
  };

  // ---------------------------------------------------------------------------
  // Catalog worlds: the recorded seven-lesson quote plus its pagination walk, the empty
  // universe, the nullable-card lesson and the tied-level pair.
  // ---------------------------------------------------------------------------
  function catalogLesson(id, {ratingSum = 0, ratingCount = 0, ratingVersion = 0, title = `Title ${id}`,
    previousShelf = "review", placementVersion = 1, updatedAtMs = PLACEMENT_AT} = {}) {
    const docs = {
      [`catalogs/catalog-${id}`]: {questType: "REGULAR"},
      [`quests/quest-${id}`]: {catalogId: `catalog-${id}`, authorUid: "author", visibleOn: ["archive"]},
      [`sections/section-${id}`]: {questId: `quest-${id}`},
      [`themes/theme-${id}`]: {sectionId: `section-${id}`},
      [`lessons/${id}`]: {themeId: `theme-${id}`, version: 1, title,
        reviewSubmissionId: `submission-${id}`, reviewApprovedAtMs: 1},
      [`lesson_placements/${id}`]: {lessonId: id, shelf: "arena", authorUid: "author",
        contentVersion: 1, ...(previousShelf === null ? {} : {previousShelf, placementVersion, updatedAtMs})},
      [`admin/review/lessons/${id}`]: {ownerUid: "author", questId: `quest-${id}`,
        submissionId: `submission-${id}`, status: "PUBLISHED", sourceLanguages: ["en"]},
      ...Object.fromEntries(["TESTING", "LOGIC"].map((kind) => [`admin/review/lessons/${id}/reviews/${kind}`, {
        kind, reviewerUid: `${kind.toLowerCase()}-${id}`, acceptedByServer: true, score: 3,
        reviewerLevelAtSubmit: 100, submissionId: `submission-${id}`,
      }])),
    };
    if (ratingCount > 0 || ratingVersion > 0) {
      docs[`lesson_ratings/${id}`] = {ratingSum, ratingCount, ratingVersion};
    }
    return docs;
  }
  const catalogUniverse = {
    high: {ratingSum: 3, ratingCount: 1, ratingVersion: 1},
    "z-near": {ratingSum: 8000000000000002, ratingCount: 4000000000000000, ratingVersion: 1},
    "a-near": {ratingSum: 8000000000000001, ratingCount: 4000000000000000, ratingVersion: 1},
    "a-unrated": {},
    "b-mid": {ratingSum: 2, ratingCount: 1, ratingVersion: 1},
    "c-mid": {ratingSum: 4, ratingCount: 2, ratingVersion: 1},
    low: {ratingSum: 1, ratingCount: 1, ratingVersion: 1},
  };
  const fullWorld = Object.assign({}, ...Object.entries(catalogUniverse)
    .map(([id, rating]) => catalogLesson(id, rating)));
  const catalog = realCreateCatalog({
    db: store.db, loadLessonContext: loadArenaLessonContext, nowMs: () => CATALOG_NOW,
  });
  const quote = async (payload) => catalog.list({uid: "player", payload});
  Date.now = () => CATALOG_NOW;
  store.reset(fullWorld);
  const pageOne = await quote({pageSize: 3});
  const pageTwo = await quote({pageSize: 3, cursor: pageOne.nextCursor});
  const catalogFixtures = {
    schemaVersion: 1,
    producer: recordedCatalog.producer,
    sourceSha256: sourceHashes(recordedCatalog.sourceSha256),
    first: pageOne,
    second: pageTwo,
    last: await quote({pageSize: 3, cursor: pageTwo.nextCursor}),
    empty: await (async () => {
      store.reset({});
      return quote({});
    })(),
    nullable: await (async () => {
      store.reset(catalogLesson("nullable", {title: null, previousShelf: null}));
      return quote({});
    })(),
    tieFirst: await (async () => {
      store.reset({
        ...catalogLesson("a-rated", {ratingSum: 2, ratingCount: 1, ratingVersion: 1}),
        ...catalogLesson("b-unrated", {}),
        ...catalogLesson("c-rated", {ratingSum: 4, ratingCount: 2, ratingVersion: 1}),
        ...catalogLesson("low", {ratingSum: 1, ratingCount: 1, ratingVersion: 1}),
      });
      return quote({pageSize: 2});
    })(),
    tieLast: await (async () => {
      store.reset({
        ...catalogLesson("a-rated", {ratingSum: 2, ratingCount: 1, ratingVersion: 1}),
        ...catalogLesson("b-unrated", {}),
        ...catalogLesson("c-rated", {ratingSum: 4, ratingCount: 2, ratingVersion: 1}),
        ...catalogLesson("low", {ratingSum: 1, ratingCount: 1, ratingVersion: 1}),
      });
      const first = await quote({pageSize: 2});
      return quote({pageSize: 2, cursor: first.nextCursor});
    })(),
  };
  // The full quote replays the whole universe after the paginated walks (each quote is its own
  // read-only transaction, so the walk's cursors never reshape it).
  store.reset(fullWorld);
  catalogFixtures.full = await quote({});
  Date.now = realNow;

  // ---------------------------------------------------------------------------
  // Compare against the recorded files, then optionally write.
  // ---------------------------------------------------------------------------
  const results = {};
  for (const [name, generated, recorded] of [
    ["arena-ticket-client-fixtures.json", ticketFixtures, recordedTickets],
    ["arena-catalog-client-fixtures.json", catalogFixtures, recordedCatalog],
  ]) {
    const drift = [];
    compareSections("", generated, recorded, drift);
    results[name] = drift;
    if (drift.length === 0) {
      console.log(`${name}: all sections byte-identical to the recorded fixture`);
    } else {
      console.log(`${name}: ${drift.length} drifted section(s):`);
      for (const line of drift) console.log(`  ${line}`);
    }
    if (doWrite) {
      fs.writeFileSync(path.join(FIXTURE_DIR, name), `${JSON.stringify(generated, null, 2)}\n`);
      console.log(`${name}: written`);
    }
  }
  const drifted = Object.values(results).reduce((sum, rows) => sum + rows.length, 0);
  if (drifted > 0 && !doWrite) process.exitCode = 1;
}

function redactionStatus(payload) {
  const {redact} = require("./question-redaction");
  return redact(payload, "HARD", {questionId: "q1"});
}

function sourceHashes(recorded) {
  const hashes = {};
  for (const name of Object.keys(recorded)) {
    hashes[name] = sha256(fs.readFileSync(path.join(__dirname, name), "utf8"));
  }
  return hashes;
}

/** The client-side journal digests recorded under `hashes` — stable canonical literals. */
function ticketHashes() {
  const digest = (value) => sha256(JSON.stringify(value));
  const selectedId = digest(["arena-ticket", "player", "client-selected"]);
  const hardId = digest(["arena-ticket", "player", "client-hard"]);
  const randomId = digest(["arena-ticket", "player", "client-random"]);
  return {
    '["arena-ticket","player","client-selected"]': digest(["arena-ticket", "player", "client-selected"]),
    [`["arena-attempt","${selectedId}"]`]: digest(["arena-attempt", selectedId]),
    '["arena-ticket","player","client-hard"]': digest(["arena-ticket", "player", "client-hard"]),
    [`["arena-attempt","${hardId}"]`]: digest(["arena-attempt", hardId]),
    '["arena-ticket","player","client-random"]': digest(["arena-ticket", "player", "client-random"]),
    [`["arena-attempt","${randomId}"]`]: digest(["arena-attempt", randomId]),
  };
}

function compareSections(prefix, generated, recorded, drift) {
  if (JSON.stringify(generated) === JSON.stringify(recorded)) return;
  if (typeof generated !== "object" || generated === null || typeof recorded !== "object" ||
      recorded === null || Array.isArray(generated) !== Array.isArray(recorded)) {
    drift.push(`${prefix || "<root>"}: ${preview(recorded)} -> ${preview(generated)}`);
    return;
  }
  const keys = [...new Set([...Object.keys(generated), ...Object.keys(recorded)])]
    .sort(compareKeysLike(recorded, generated));
  for (const key of keys) {
    if (!(key in generated)) {
      drift.push(`${prefix}${key}: section missing from regenerated output`);
    } else if (!(key in recorded)) {
      drift.push(`${prefix}${key}: new section in regenerated output`);
    } else {
      compareSections(`${prefix}${key}.`, generated[key], recorded[key], drift);
    }
  }
}

/** Keep the recorded file's key order at the top level; sort deeper for readable diffs. */
function compareKeysLike(recorded, generated) {
  const order = Object.keys(recorded);
  return (left, right) => {
    const leftIndex = order.indexOf(left);
    const rightIndex = order.indexOf(right);
    if (leftIndex !== -1 || rightIndex !== -1) {
      return (leftIndex === -1 ? order.length : leftIndex) - (rightIndex === -1 ? order.length : rightIndex);
    }
    return Object.keys(generated).indexOf(left) - Object.keys(generated).indexOf(right) || (left < right ? -1 : 1);
  };
}

function preview(value) {
  const text = JSON.stringify(value);
  return text.length > 160 ? `${text.slice(0, 157)}...` : text;
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
