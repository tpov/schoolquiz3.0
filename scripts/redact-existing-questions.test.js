"use strict";

/**
 * The walk, the print and the write of `redact-existing-questions.js`, driven against a fake
 * Firestore that remembers every query, read, write and commit.
 *
 * The decision is the planner's and is proved in `functions/`; what is proved here is the part
 * that touches the world: that a dry run touches nothing, that a write puts down exactly the
 * planned documents and none withheld, that the walk follows its cursor and stops where the
 * catalog stops, that a document which moved between the read and the commit is not overwritten,
 * and that a batch is closed by count and by size. In the style of `content-catalog-index.test.js`.
 * These tests are in no gate; `scripts/package.json` runs them by hand.
 */

const assert = require("assert");
const {
  UsageError,
  BATCH_WRITES,
  BATCH_BYTES,
  parseArguments,
  checkProject,
  readCatalog,
  writeKeyDocuments,
  printReport,
  execute,
} = require("./redact-existing-questions");
const {planCatalogRedaction} = require("../functions/catalog-redaction-plan");
const {KEY_COLLECTION, keyDocumentPath} = require("../functions/question-key-store");
const {SINGLE, MULTIPLE, SURVEY, LEGACY_SINGLE_CHOICE} = require("../functions/_question-fixtures");
const {REDACTED_TYPE, CONTENT_TYPE} = require("../functions/question-redaction");

const json = JSON.stringify;
const DOCUMENT_ID = "__name__";

/**
 * Enough of Firestore for the script: paged queries by document id, `getAll`, and batches whose
 * `commit` enforces `create` and `lastUpdateTime` the way the real one does. `hooks.beforeCommit`
 * runs between the script's read and its commit, which is where a publication lands.
 */
function fakeFirestore(seed, hooks) {
  const store = new Map();
  let clock = 1;
  const stamp = () => {
    const seq = clock;
    clock += 1;
    return {seq, isEqual: (other) => Boolean(other) && other.seq === seq};
  };
  for (const [collection, docs] of Object.entries(seed || {})) {
    for (const [id, data] of Object.entries(docs)) store.set(`${collection}/${id}`, {data, updateTime: stamp()});
  }
  const log = [];
  const idOf = (docPath) => docPath.slice(docPath.indexOf("/") + 1);
  const snapshot = (docPath) => {
    const held = store.get(docPath);
    return {
      id: idOf(docPath),
      exists: Boolean(held),
      updateTime: held ? held.updateTime : undefined,
      data: () => (held ? held.data : undefined),
    };
  };
  function query(name, state) {
    return {
      orderBy(field) {
        assert.strictEqual(field, DOCUMENT_ID, "the walk must order by document id");
        return query(name, {...state, ordered: true});
      },
      limit(count) {
        return query(name, {...state, limit: count});
      },
      startAfter(doc) {
        return query(name, {...state, after: doc.id});
      },
      async get() {
        assert.ok(state.ordered, "a cursor without an order is meaningless");
        log.push({op: "query", limit: state.limit, after: state.after || null});
        const ids = [...store.keys()].filter((docPath) => docPath.startsWith(`${name}/`)).map(idOf).sort();
        const from = state.after ? ids.filter((id) => id > state.after) : ids;
        const docs = from.slice(0, state.limit).map((id) => snapshot(`${name}/${id}`));
        return {docs, size: docs.length};
      },
    };
  }
  const db = {
    collection: (name) => query(name, {}),
    doc: (docPath) => ({path: docPath, id: idOf(docPath)}),
    async getAll(...refs) {
      log.push({op: "getAll", count: refs.length});
      return refs.map((ref) => snapshot(ref.path));
    },
    batch() {
      const ops = [];
      return {
        set(ref, data, options) {
          ops.push({op: "set", path: ref.path, data, options});
        },
        create(ref, data) {
          ops.push({op: "create", path: ref.path, data});
        },
        update(ref, data, precondition) {
          ops.push({op: "update", path: ref.path, data, precondition});
        },
        async commit() {
          log.push({op: "commit", count: ops.length, paths: ops.map((write) => write.path)});
          if (hooks && hooks.beforeCommit) hooks.beforeCommit({store, stamp, commits: log.filter((e) => e.op === "commit").length});
          for (const write of ops) {
            const current = store.get(write.path);
            if (write.op === "create" && current) throw new Error(`6 ALREADY_EXISTS: ${write.path}`);
            if (write.op === "update") {
              if (!current) throw new Error(`5 NOT_FOUND: ${write.path}`);
              const wanted = write.precondition && write.precondition.lastUpdateTime;
              if (wanted && !wanted.isEqual(current.updateTime)) {
                throw new Error("9 FAILED_PRECONDITION: the stored version does not match the required base version");
              }
            }
          }
          for (const write of ops) {
            store.set(write.path, {data: write.data, updateTime: stamp()});
            log.push({op: write.op, path: write.path, precondition: write.precondition || null});
          }
          ops.length = 0;
        },
      };
    },
  };
  return {
    db,
    store,
    log,
    FieldPath: {documentId: () => DOCUMENT_ID},
    writes: () => log.filter((entry) => ["set", "create", "update"].includes(entry.op)),
    commits: () => log.filter((entry) => entry.op === "commit"),
    queries: () => log.filter((entry) => entry.op === "query"),
  };
}

function questionDoc(lessonId, payload, fields) {
  return {
    lessonId,
    text: payload && payload.text ? payload.text : "",
    payload: typeof payload === "string" ? payload : json(payload),
    language: "ru",
    order: 0,
    version: 1,
    lastModifiedAt: 0,
    archived: false,
    ...fields,
  };
}

/** Three lessons: one plain, one with a refusal and a survey, one that must be withheld. */
const REDACTED_SINGLE = {type: REDACTED_TYPE[CONTENT_TYPE.SINGLE_CHOICE], text: "?", imageUrl: null, options: SINGLE.options};
const CATALOG = {
  q01: questionDoc("lesson-a", SINGLE),
  q02: questionDoc("lesson-b", MULTIPLE),
  q03: questionDoc("lesson-a", SURVEY),
  q04: questionDoc("lesson-b", LEGACY_SINGLE_CHOICE),
  q05: questionDoc("lesson-w", REDACTED_SINGLE),
  q06: questionDoc("lesson-w", SINGLE),
  q07: questionDoc("lesson-a", SINGLE, {archived: true}),
};
/** The plan the script must arrive at, built from the same seed in the same (id) order. */
function expectedPlan(catalog) {
  const ids = Object.keys(catalog).sort();
  return planCatalogRedaction(ids.map((id) => ({id, data: {id, ...catalog[id]}})));
}
function io(fake) {
  const out = [];
  const err = [];
  return {db: fake.db, FieldPath: fake.FieldPath, target: "fake", out: (text) => out.push(text), err: (text) => err.push(text), lines: out, progress: err};
}

// ---------------------------------------------------------------------------------------------

function testArgumentsAreParsedAndRefused() {
  assert.deepStrictEqual(parseArguments([]), {writeKeys: false, pageSize: 500, project: null, help: false});
  assert.deepStrictEqual(parseArguments(["--write-keys", "--project", "school-quiz", "--page-size", "200"]),
    {writeKeys: true, pageSize: 200, project: "school-quiz", help: false});
  assert.strictEqual(parseArguments(["-h"]).help, true);
  for (const argv of [["--bogus"], ["--page-size", "0"], ["--page-size", "x"], ["--page-size"], ["--project"], ["--project", "--write-keys"]]) {
    assert.throws(() => parseArguments(argv), UsageError, argv.join(" "));
  }
}

function testAWriteAgainstARealProjectMustNameIt() {
  const real = {emulatorHost: null, credentialProjectId: "school-quiz"};
  assert.strictEqual(checkProject({writeKeys: false, project: null}, real), null, "a dry run needs no project");
  assert.strictEqual(checkProject({writeKeys: true, project: null}, {emulatorHost: "127.0.0.1:8080"}), null, "the emulator needs none");
  assert.match(checkProject({writeKeys: true, project: null}, real), /--project/);
  assert.match(checkProject({writeKeys: true, project: "other-project"}, real), /does not match/);
  assert.strictEqual(checkProject({writeKeys: true, project: "school-quiz"}, real), null);
}

async function testTheWalkFollowsItsCursorAndStopsOnAShortPage() {
  const fake = fakeFirestore({questions: {q1: {}, q2: {}, q3: {}, q4: {}, q5: {}}});
  const seen = [];
  const {rows, pages} = await readCatalog(fake.db, fake.FieldPath, 2, (page) => seen.push(page));

  assert.deepStrictEqual(rows.map((row) => row.id), ["q1", "q2", "q3", "q4", "q5"]);
  assert.strictEqual(pages, 3);
  assert.deepStrictEqual(fake.queries().map((entry) => entry.after), [null, "q2", "q4"], "each page starts after the last document of the one before");
  assert.strictEqual(fake.queries().length, 3, "a short page ends the walk; no query follows it");
  assert.deepStrictEqual(seen.map((page) => [page.page, page.size, page.total]), [[1, 2, 2], [2, 2, 4], [3, 1, 5]]);
}

async function testACatalogThatIsAMultipleOfThePageSizeCountsItsPages() {
  const fake = fakeFirestore({questions: {q1: {}, q2: {}, q3: {}, q4: {}}});
  const {rows, pages} = await readCatalog(fake.db, fake.FieldPath, 2);
  assert.strictEqual(rows.length, 4);
  assert.strictEqual(pages, 2, "the trailing empty query is not a page");
  assert.strictEqual(fake.queries().length, 3, "the walk cannot know the catalog ended without asking once more");

  const empty = fakeFirestore({questions: {}});
  const nothing = await readCatalog(empty.db, empty.FieldPath, 2);
  assert.deepStrictEqual(nothing, {rows: [], pages: 0});
}

async function testADryRunIssuesNoWrite() {
  const fake = fakeFirestore({questions: CATALOG, [KEY_COLLECTION]: {"lesson-a": {stale: true}}});
  const before = new Map([...fake.store].map(([docPath, held]) => [docPath, held.updateTime.seq]));
  const context = io(fake);
  const {written} = await execute({writeKeys: false, pageSize: 2}, context);

  assert.strictEqual(written, 0);
  assert.deepStrictEqual(fake.writes(), []);
  assert.deepStrictEqual(fake.commits(), []);
  assert.strictEqual(fake.log.filter((entry) => entry.op === "getAll").length, 0, "a dry run does not even read the key store");
  assert.deepStrictEqual(new Map([...fake.store].map(([docPath, held]) => [docPath, held.updateTime.seq])), before);
  assert.ok(context.lines[0].includes("DRY RUN"));
  assert.ok(context.lines.some((text) => text.startsWith("dry run: nothing written")));
  assert.deepStrictEqual(context.progress, ["page 1: 2 document(s), 2 so far", "page 2: 2 document(s), 4 so far", "page 3: 2 document(s), 6 so far", "page 4: 1 document(s), 7 so far"]);
}

async function testAWritePutsDownExactlyThePlannedDocuments() {
  const fake = fakeFirestore({questions: CATALOG, [KEY_COLLECTION]: {"lesson-a": {stale: true}}});
  const staleTime = fake.store.get(keyDocumentPath("lesson-a")).updateTime;
  const context = io(fake);
  const {plan, written} = await execute({writeKeys: true, pageSize: 3}, context);
  const expected = expectedPlan(CATALOG);

  assert.deepStrictEqual(plan.documents, expected.documents);
  assert.deepStrictEqual(Object.keys(plan.documents).sort(), [keyDocumentPath("lesson-a"), keyDocumentPath("lesson-b")]);
  assert.deepStrictEqual(plan.withheld.map((record) => record.path), [keyDocumentPath("lesson-w")]);

  const writes = fake.writes();
  assert.deepStrictEqual(writes.map((write) => write.path).sort(), Object.keys(plan.documents).sort(), "exactly the planned paths, nothing withheld");
  assert.ok(writes.every((write) => write.path.startsWith(`${KEY_COLLECTION}/`)));
  assert.strictEqual(written, 2);
  assert.strictEqual(fake.commits().length, 1);

  // The document that existed is updated under the time it was read at; the new one is created.
  const updated = writes.find((write) => write.path === keyDocumentPath("lesson-a"));
  assert.strictEqual(updated.op, "update");
  assert.ok(updated.precondition && updated.precondition.lastUpdateTime.isEqual(staleTime));
  const created = writes.find((write) => write.path === keyDocumentPath("lesson-b"));
  assert.strictEqual(created.op, "create");

  for (const [docPath, data] of Object.entries(plan.documents)) {
    assert.deepStrictEqual(fake.store.get(docPath).data, data, docPath);
  }
  assert.strictEqual(fake.store.has(keyDocumentPath("lesson-w")), false, "the withheld lesson was not written");
  assert.ok(context.lines.some((text) => text.startsWith("written: 2 question_keys document(s), every one publicHalfRedacted: false")));
  assert.ok(context.lines.some((text) => text.startsWith("withheld: 1 lesson(s) not written")));
  assert.ok(context.lines.some((text) => text.includes("next step: republish")));
}

async function testAPublicationLandingMidRunIsNotOverwritten() {
  // Between the script's read of the key store and its commit, publication rewrites lesson-a's
  // document. The stale plan must not land on top of it.
  const published = {id: "lesson-a", keys: [{questionId: "q01", key: {fresh: true}}], refusals: [], omitted: 0, version: 1, publicHalfRedacted: true};
  const fake = fakeFirestore({questions: CATALOG, [KEY_COLLECTION]: {"lesson-a": {stale: true}}}, {
    beforeCommit: ({store, stamp}) => store.set(keyDocumentPath("lesson-a"), {data: published, updateTime: stamp()}),
  });
  const plan = expectedPlan(CATALOG);

  await assert.rejects(
    writeKeyDocuments(fake.db, plan.documents),
    (error) => {
      assert.match(error.message, /commit failed after 0 document\(s\) had landed/);
      assert.match(error.message, /question_keys\/lesson-a/);
      assert.deepStrictEqual(error.conflicts, [keyDocumentPath("lesson-a")]);
      assert.strictEqual(error.landed, 0);
      return true;
    },
  );
  assert.deepStrictEqual(fake.store.get(keyDocumentPath("lesson-a")).data, published, "the publication survived");
  assert.strictEqual(fake.store.has(keyDocumentPath("lesson-b")), false, "a failed commit lands nothing from its batch");
}

async function testCommitsAreChunkedByCountAndByBytes() {
  const many = {};
  for (let index = 0; index < 1000; index += 1) many[keyDocumentPath(`lesson-${String(index).padStart(4, "0")}`)] = {id: `lesson-${index}`, keys: []};
  const byCount = fakeFirestore({});
  const {written} = await writeKeyDocuments(byCount.db, many);
  assert.strictEqual(written, 1000);
  assert.deepStrictEqual(byCount.commits().map((entry) => entry.count), [BATCH_WRITES, BATCH_WRITES, 1000 - 2 * BATCH_WRITES]);
  assert.strictEqual(byCount.log.filter((entry) => entry.op === "getAll").length, 4, "reads are chunked too");

  // Three documents of 3 MiB each: two fit under the byte budget, the third does not.
  const big = {};
  const third = BATCH_BYTES / 8 * 3;
  for (const lessonId of ["big-1", "big-2", "big-3"]) big[keyDocumentPath(lessonId)] = {id: lessonId, keys: [{questionId: "q", key: {blob: "x".repeat(third)}}]};
  const byBytes = fakeFirestore({});
  await writeKeyDocuments(byBytes.db, big);
  assert.deepStrictEqual(byBytes.commits().map((entry) => entry.count), [2, 1]);
}

async function testAFailedCommitSaysHowManyLanded() {
  const many = {};
  for (let index = 0; index < 500; index += 1) many[keyDocumentPath(`lesson-${String(index).padStart(4, "0")}`)] = {id: `lesson-${index}`, keys: []};
  const fake = fakeFirestore({}, {
    // The second commit meets a document that appeared in between.
    beforeCommit: ({store, stamp, commits}) => {
      if (commits === 2) store.set(keyDocumentPath("lesson-0470"), {data: {published: true}, updateTime: stamp()});
    },
  });
  const context = io(fake);
  context.db = fake.db;
  await assert.rejects(
    writeKeyDocuments(fake.db, many),
    (error) => error.landed === BATCH_WRITES && error.conflicts.length === 1 && /lesson-0470/.test(error.message),
  );
}

async function testTheReportNamesDocumentsAndSortsRefusals() {
  const catalog = {
    "doc-z": questionDoc("lesson-b", "{not json"),
    "doc-a": questionDoc("lesson-b", {...SINGLE, correctOptionId: "zzz"}),
    "doc-m": questionDoc("lesson-a", LEGACY_SINGLE_CHOICE),
    "doc-k": questionDoc("lesson-a", SINGLE),
  };
  catalog["doc-a"].id = "named-a";
  const plan = expectedPlan(catalog);
  const lines = [];
  printReport(plan, {writeKeys: false, pageSize: 500, target: "fake", pages: 1}, (text) => lines.push(text));

  const start = lines.findIndex((text) => text.startsWith("refusals (3)"));
  assert.ok(start >= 0, "the refusal section names its count");
  assert.match(lines[start + 1], /lesson\s+document\s+question id\s+reason/);
  const listed = lines.slice(start + 2, start + 5).map((text) => text.trim().split(/\s{2,}/));
  assert.deepStrictEqual(listed.map((cells) => cells.slice(0, 4)), [
    ["lesson-a", "doc-m", "doc-m", "unknown-type"],
    ["lesson-b", "doc-a", "named-a", "dangling-correct-option"],
    ["lesson-b", "doc-z", "doc-z", "malformed-json"],
  ], "sorted by lesson then document id, the id refused under beside the document to open");
  assert.ok(lines.some((text) => /legacy single-choice dialect \(in unknown-type\)\s*: 1$/.test(text)), "the legacy line is always printed");
  assert.ok(lines.some((text) => text.includes("a quest's shelf is not consulted")));
  assert.ok(lines.some((text) => /key documents to write\s*: 2  \(of which with no keys: 1\)/.test(text)), "the empty document is marked");
  assert.ok(!lines.some((text) => /\bindex\b/.test(text)), "the index is a position in a list nobody sees");
}

(async () => {
  testArgumentsAreParsedAndRefused();
  testAWriteAgainstARealProjectMustNameIt();
  await testTheWalkFollowsItsCursorAndStopsOnAShortPage();
  await testACatalogThatIsAMultipleOfThePageSizeCountsItsPages();
  await testADryRunIssuesNoWrite();
  await testAWritePutsDownExactlyThePlannedDocuments();
  await testAPublicationLandingMidRunIsNotOverwritten();
  await testCommitsAreChunkedByCountAndByBytes();
  await testAFailedCommitSaysHowManyLanded();
  await testTheReportNamesDocumentsAndSortsRefusals();
  console.log("redact-existing-questions.test.js OK (10 cases)");
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
