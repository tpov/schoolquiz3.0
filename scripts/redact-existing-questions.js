"use strict";

/**
 * What redaction would do to the live catalog — reported first, and written only on request.
 *
 * Every question in production was written straight into `questions/{id}` by a seed script and
 * never went through publication, so the key store (`functions/question-key-store.js`) has never
 * seen any of them, and switching redaction on at publication would change nothing that exists.
 * This walks the whole `questions` collection, hands it to `functions/catalog-redaction-plan.js`,
 * and prints what that planner decided: per lesson and overall, how many questions get a key, how
 * many are refused and why, each refusal by the id it was refused under and the document it lives
 * in.
 *
 * It never touches a payload. With `--write-keys` it writes one `question_keys/{lessonId}`
 * document per lesson — the document publication builds for those questions — and nothing else.
 * A second run replaces each document whole, the way a republish does. Clients cannot play a
 * redacted question yet, and the step that rewrites payloads has to take both halves from a
 * single `redact` call, which a key-only pass cannot promise; so the rewrite is not here, by
 * design. The planner is the decision and is gated by `cd functions && npm test`; this file is
 * the walk, the print and the write, and `redact-existing-questions.test.js` beside it drives
 * those against a fake Firestore.
 *
 * Two guards on the write:
 *
 * - Every document is written under a precondition — `create` where none exists, `update` against
 *   the `updateTime` read just before where one does — so a publication that lands between the
 *   walk and the write is not overwritten from a stale read. A conflict names the lessons that
 *   moved, says how many documents landed before it, and exits 1.
 * - Against a real project, `--write-keys` requires `--project <id>` matching the credential's
 *   `project_id`. A service-account key left in the shell must not turn a mistyped dry run into a
 *   production write.
 *
 * `archived` is honoured on the question document only — the flag `questionRowFor` in `index.js`
 * reads on the reward path. Nothing writes it there today (`setPublicQuestShelf` archives the
 * quest), so a question under an archived quest is keyed like any other; the quest chain is not
 * consulted here, and the report says so.
 *
 * Environment — credentials come from here, never from a path in this file:
 *   FIRESTORE_EMULATOR_HOST         host:port of a Firestore emulator. Wins when set.
 *   GCLOUD_PROJECT                  the emulator's project id (default demo-schoolquiz).
 *   GOOGLE_APPLICATION_CREDENTIALS  path to a service-account JSON, for a real project.
 *
 * Usage:
 *   node scripts/redact-existing-questions.js                               # dry run: the report, no writes
 *   node scripts/redact-existing-questions.js --write-keys --project <id>   # the report, then the key documents
 *   node scripts/redact-existing-questions.js --write-keys                  # the same on the emulator
 *   node scripts/redact-existing-questions.js --page-size 200               # walk the catalog in smaller pages
 *   node scripts/redact-existing-questions.js --help
 *
 * Exits 0 when the walk — and the write, if asked for — completed, whatever the report says: a
 * refusal is a finding, not a failure. Anything else exits 1.
 */

const fs = require("fs");
const path = require("path");
const {planCatalogRedaction, DIFFICULTY} = require("../functions/catalog-redaction-plan");
const {KEY_COLLECTION, PUBLIC_HALF_REDACTED} = require("../functions/question-key-store");

const QUESTIONS_COLLECTION = "questions";
const DEFAULT_PAGE_SIZE = 500;
const DEFAULT_EMULATOR_PROJECT = "demo-schoolquiz";
/** Firestore takes 500 writes per batch; publication chunks at 450 (`index.js`, `commitOperations`). */
const BATCH_WRITES = 450;
/**
 * Firestore's cap on one commit request is 10 MiB, and a key document may run to ~0.9 MiB
 * (`MAX_DOCUMENT_BYTES`), so a batch is closed by bytes as well as by count. Measured as JSON,
 * which is a proxy for the wire size; the headroom covers the difference.
 */
const BATCH_BYTES = 8 * 1024 * 1024;
/** Documents per `getAll`, as `verify-seeded-quest.js` chunks its reads. */
const READ_CHUNK = 250;

const USAGE = `usage: node scripts/redact-existing-questions.js [--write-keys [--project <id>]] [--page-size <n>] [--help]

  (no flags)        dry run: walk the catalog and print the report; nothing is written
  --write-keys      after the report, write one ${KEY_COLLECTION}/{lessonId} document per lesson
  --project <id>    required with --write-keys against a real project; must match the credential's project_id
  --page-size <n>   question documents to read per query (default ${DEFAULT_PAGE_SIZE})
  --help, -h        print this and exit

environment:
  FIRESTORE_EMULATOR_HOST         use the emulator at host:port; GCLOUD_PROJECT names its project
  GOOGLE_APPLICATION_CREDENTIALS  service-account JSON for a real project, when no emulator is set
`;

/** A mistake in how the tool was invoked: printed with the usage, without a stack. */
class UsageError extends Error {}

function parseArguments(argv) {
  const options = {writeKeys: false, pageSize: DEFAULT_PAGE_SIZE, project: null, help: false};
  for (let at = 0; at < argv.length; at += 1) {
    const argument = argv[at];
    if (argument === "--help" || argument === "-h") {
      options.help = true;
    } else if (argument === "--write-keys") {
      options.writeKeys = true;
    } else if (argument === "--project") {
      at += 1;
      const project = argv[at];
      if (typeof project !== "string" || project === "" || project.startsWith("--")) {
        throw new UsageError("--project needs a project id");
      }
      options.project = project;
    } else if (argument === "--page-size") {
      at += 1;
      const size = Number(argv[at]);
      if (!Number.isInteger(size) || size < 1) {
        throw new UsageError(`--page-size needs a positive integer, got ${JSON.stringify(argv[at])}`);
      }
      options.pageSize = size;
    } else {
      throw new UsageError(`unknown argument ${JSON.stringify(argument)}`);
    }
  }
  return options;
}

/**
 * Whether a write may proceed against what the environment names. Returns the objection, or null.
 *
 * A dry run needs no project: it writes nothing. The emulator needs none: there is nothing there
 * to lose. A real project needs the id said out loud and matching the credential, so the
 * credential alone cannot choose the target.
 */
function checkProject(options, environment) {
  if (!options.writeKeys || environment.emulatorHost) return null;
  if (!options.project) {
    return `--write-keys against a real project needs --project <id>; the credential belongs to ${environment.credentialProjectId}.`;
  }
  if (options.project !== environment.credentialProjectId) {
    return `--project ${options.project} does not match the credential's project_id ${environment.credentialProjectId}; refusing to write.`;
  }
  return null;
}

/** The service-account file, or a usage error that names what is wrong with it. */
function loadCredential(keyPath) {
  const resolved = path.resolve(keyPath);
  let text;
  try {
    text = fs.readFileSync(resolved, "utf8");
  } catch (error) {
    throw new UsageError(`GOOGLE_APPLICATION_CREDENTIALS points at ${resolved}, which cannot be read: ${error.message}`);
  }
  let serviceAccount;
  try {
    serviceAccount = JSON.parse(text);
  } catch (error) {
    throw new UsageError(`GOOGLE_APPLICATION_CREDENTIALS points at ${resolved}, which is not JSON: ${error.message}`);
  }
  if (!serviceAccount || typeof serviceAccount.project_id !== "string" || serviceAccount.project_id === "") {
    throw new UsageError(`GOOGLE_APPLICATION_CREDENTIALS points at ${resolved}, which has no project_id; is it a service-account key?`);
  }
  return {serviceAccount, resolved};
}

/**
 * Loads the SDK and opens Firestore against whatever the environment names.
 *
 * Required here rather than at the top so `--help` never loads `firebase-admin`, let alone lets
 * it look for credentials. The emulator wins over a service account so a key left in the shell
 * cannot make an emulator run reach production.
 */
function connect(options) {
  const admin = require("firebase-admin");
  const emulatorHost = process.env.FIRESTORE_EMULATOR_HOST;
  if (emulatorHost) {
    const projectId = process.env.GCLOUD_PROJECT || DEFAULT_EMULATOR_PROJECT;
    admin.initializeApp({projectId});
    return {db: admin.firestore(), FieldPath: admin.firestore.FieldPath, target: `emulator ${emulatorHost}, project ${projectId}`};
  }
  const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!keyPath) {
    throw new UsageError(
      "No credentials: set GOOGLE_APPLICATION_CREDENTIALS to a service-account JSON, " +
      "or FIRESTORE_EMULATOR_HOST to use the emulator.",
    );
  }
  const {serviceAccount, resolved} = loadCredential(keyPath);
  const problem = checkProject(options, {emulatorHost: null, credentialProjectId: serviceAccount.project_id});
  if (problem) throw new UsageError(problem);
  admin.initializeApp({credential: admin.credential.cert(serviceAccount)});
  return {
    db: admin.firestore(),
    FieldPath: admin.firestore.FieldPath,
    target: `project ${serviceAccount.project_id} (${resolved})`,
  };
}

/**
 * The whole collection, page by page, in document-id order.
 *
 * Everything is collected before anything is planned: a lesson's questions are not adjacent in
 * id order, so any page boundary can fall inside a lesson, and a lesson planned from one page at
 * a time would be written as two documents, the second replacing the first.
 *
 * @param onPage called after each page with `{page, size, total}`, so a long walk is not silent.
 */
async function readCatalog(db, FieldPath, pageSize, onPage) {
  const rows = [];
  let pages = 0;
  let last = null;
  for (;;) {
    let query = db.collection(QUESTIONS_COLLECTION).orderBy(FieldPath.documentId()).limit(pageSize);
    if (last) query = query.startAfter(last);
    const snapshot = await query.get();
    // An empty page is the end, not a page: a catalog of exactly N × pageSize documents ends on
    // one, and it should not be counted as if something had been read.
    if (snapshot.size === 0) break;
    pages += 1;
    for (const doc of snapshot.docs) rows.push({id: doc.id, data: doc.data() || {}});
    if (onPage) onPage({page: pages, size: snapshot.size, total: rows.length});
    if (snapshot.size < pageSize) break;
    last = snapshot.docs[snapshot.docs.length - 1];
  }
  return {rows, pages};
}

/** `path → updateTime`, null where the document does not exist. */
async function readUpdateTimes(db, paths) {
  const times = new Map();
  for (let at = 0; at < paths.length; at += READ_CHUNK) {
    const chunk = paths.slice(at, at + READ_CHUNK);
    const snapshots = await db.getAll(...chunk.map((docPath) => db.doc(docPath)));
    snapshots.forEach((snapshot, index) => times.set(chunk[index], snapshot.exists ? snapshot.updateTime : null));
  }
  return times;
}

/** Which of `paths` no longer match the update times in `existing`. */
async function changedSince(db, paths, existing) {
  const now = await readUpdateTimes(db, paths);
  return paths.filter((docPath) => {
    const before = existing.get(docPath) || null;
    const after = now.get(docPath) || null;
    if (before === null || after === null) return before !== after;
    return !before.isEqual(after);
  });
}

/**
 * Writes the planned documents, and only those.
 *
 * Each write insists on the document being as the read just before found it: `create` where
 * there was none, `update` with `lastUpdateTime` where there was one. `update` rather than `set`
 * because only `update` takes a precondition; every field the document has is in the data, so the
 * result is a replacement in all but name. A commit that fails is reported with the number of
 * documents that had already landed and the lessons whose documents moved in between.
 *
 * Each path is checked against the key collection before the first read; this tool has no
 * business anywhere else.
 *
 * @returns `{written}` — documents committed.
 */
async function writeKeyDocuments(db, documents) {
  const entries = Object.entries(documents);
  for (const [docPath] of entries) {
    if (!docPath.startsWith(`${KEY_COLLECTION}/`)) {
      throw new Error(`refusing to write outside ${KEY_COLLECTION}: ${docPath}`);
    }
  }
  const existing = await readUpdateTimes(db, entries.map(([docPath]) => docPath));

  let batch = db.batch();
  let pending = [];
  let pendingBytes = 0;
  let written = 0;

  const flush = async () => {
    if (pending.length === 0) return;
    try {
      await batch.commit();
    } catch (error) {
      const conflicts = await changedSince(db, pending, existing);
      const failure = new Error(
        `commit failed after ${written} document(s) had landed: ${error.message}` +
        (conflicts.length > 0 ? `; changed since the walk read them: ${conflicts.join(", ")}` : ""),
      );
      failure.landed = written;
      failure.conflicts = conflicts;
      throw failure;
    }
    written += pending.length;
    batch = db.batch();
    pending = [];
    pendingBytes = 0;
  };

  for (const [docPath, data] of entries) {
    const size = Buffer.byteLength(JSON.stringify(data), "utf8");
    if (pending.length >= BATCH_WRITES || (pending.length > 0 && pendingBytes + size > BATCH_BYTES)) {
      await flush();
    }
    const ref = db.doc(docPath);
    const updateTime = existing.get(docPath);
    if (updateTime) batch.update(ref, data, {lastUpdateTime: updateTime});
    else batch.create(ref, data);
    pending.push(docPath);
    pendingBytes += size;
  }
  await flush();
  return {written};
}

// ---------------------------------------------------------------------------------------------
// The report.

function difficultyText(difficulty) {
  return `EASY ${difficulty[DIFFICULTY.EASY]}, HARD ${difficulty[DIFFICULTY.HARD]}, ` +
    `unreadable ${difficulty[DIFFICULTY.UNREADABLE]}`;
}

function formatTable(rows) {
  const widths = rows.reduce((acc, cells) => cells.map((cell, column) => Math.max(acc[column] || 0, String(cell).length)), []);
  return rows.map((cells) => `  ${cells.map((cell, column) => String(cell).padEnd(widths[column])).join("  ")}`.trimEnd());
}

function compareRefusals(a, b) {
  if (a.lessonId !== b.lessonId) return a.lessonId < b.lessonId ? -1 : 1;
  if (a.documentId !== b.documentId) return a.documentId < b.documentId ? -1 : 1;
  return 0;
}

/**
 * Prints the plan. `log` takes one line at a time; it defaults to `console.log`.
 *
 * The report is the deliverable: the numbers an operator acts on are `keys to write` and
 * `key documents to write`, so those count only what `--write-keys` would actually put down.
 */
function printReport(plan, context, log) {
  const out = log || console.log;
  const line = (label, value) => out(`  ${label.padEnd(44)}: ${value}`);
  const {overall, lessons} = plan.summary;
  const documentCount = Object.keys(plan.documents).length;

  out(`redact-existing-questions — ${context.writeKeys ? "WRITE MODE (--write-keys)" : "DRY RUN (nothing is written)"}`);
  out(`target: ${context.target}`);
  out(
    `catalog: ${overall.questions} question document(s) in ${context.pages} page(s) of ${context.pageSize}, ` +
    `${lessons.length} lesson(s)`,
  );
  out("");

  out("overall");
  line("archived (skipped)", `${overall.archived}  (question-level archived only; a quest's shelf is not consulted)`);
  line("considered", overall.considered);
  line("keys to write", overall.keyed);
  line("keys withheld", overall.keysWithheld);
  line("not applicable (survey)", overall.notApplicable);
  line("refused", overall.refused);
  for (const reason of Object.keys(overall.refusedByReason).sort()) {
    line(`  ${reason}`, overall.refusedByReason[reason]);
  }
  // The legacy dialect is refused as unknown-type by design, and it is the one refusal class
  // with a known fix, so it has its own line whatever the count.
  line("legacy single-choice dialect (in unknown-type)", overall.legacyDialect);
  line("difficulty (from payload)", difficultyText(overall.difficulty));
  line("translated variants (base in lesson)", overall.translatedVariants);
  line("variants without a base in the lesson", overall.variantsWithoutBase);
  line("key documents to write", `${documentCount}  (of which with no keys: ${overall.emptyDocuments})`);
  line("lessons withheld", plan.withheld.length);
  out("");

  out("per lesson");
  const table = [["lesson", "questions", "archived", "keys", "withheld", "n/a", "refused", "EASY", "HARD", "unreadable", "legacy", "variants", "document"]];
  for (const lesson of lessons) {
    let document = "none";
    if (lesson.document) document = lesson.emptyDocument ? "write (no keys)" : "write";
    else if (lesson.withheld) document = `withheld (${lesson.withheld.reason})`;
    table.push([
      lesson.lessonId === "" ? "(no lesson id)" : lesson.lessonId,
      lesson.questions,
      lesson.archived,
      lesson.keyed,
      lesson.keysWithheld,
      lesson.notApplicable,
      lesson.refused,
      lesson.difficulty[DIFFICULTY.EASY],
      lesson.difficulty[DIFFICULTY.HARD],
      lesson.difficulty[DIFFICULTY.UNREADABLE],
      lesson.legacyDialect,
      lesson.translatedVariants,
      document,
    ]);
  }
  for (const text of formatTable(table)) out(text);
  out("");

  out(`refusals (${plan.refusals.length})`);
  if (plan.refusals.length > 0) {
    const rows = [["lesson", "document", "question id", "reason", "detail"]];
    for (const record of plan.refusals.slice().sort(compareRefusals)) {
      rows.push([
        record.lessonId === "" ? "(no lesson id)" : record.lessonId,
        record.documentId === "" ? "(none)" : record.documentId,
        record.questionId === "" ? "(none)" : record.questionId,
        record.reason,
        record.detail === null || record.detail === undefined ? "" : record.detail,
      ]);
    }
    for (const text of formatTable(rows)) out(text);
  }
  out("");

  if (plan.withheld.length > 0) {
    out(`withheld (${plan.withheld.length}) — a stored key would be replaced with nothing, so these are left as they are`);
    for (const record of plan.withheld) {
      out(`  ${record.path}  ${record.reason}  ${record.questionIds.join(", ")}`);
    }
    out("  next step: republish these lessons once publication writes both halves from one redact() call;");
    out("  this tool will keep withholding them until then.");
    out("");
  }
}

/**
 * The run, with everything that talks to the world handed in: `io` is `{db, FieldPath, target,
 * out, err}`, `out` and `err` taking one line each. `main` supplies Firestore and the console;
 * the test supplies a fake and two arrays.
 */
async function execute(options, io) {
  const {rows, pages} = await readCatalog(io.db, io.FieldPath, options.pageSize, ({page, size, total}) => {
    io.err(`page ${page}: ${size} document(s), ${total} so far`);
  });
  const plan = planCatalogRedaction(rows);
  printReport(plan, {writeKeys: options.writeKeys, pageSize: options.pageSize, target: io.target, pages}, io.out);

  if (!options.writeKeys) {
    io.out("dry run: nothing written. Re-run with --write-keys to write the key documents.");
    return {plan, written: 0};
  }
  let written;
  try {
    ({written} = await writeKeyDocuments(io.db, plan.documents));
  } catch (error) {
    if (typeof error.landed === "number") {
      io.out(`written: ${error.landed} ${KEY_COLLECTION} document(s) before the failure`);
    }
    throw error;
  }
  io.out(
    `written: ${written} ${KEY_COLLECTION} document(s), every one publicHalfRedacted: ${PUBLIC_HALF_REDACTED} ` +
    "— keys for payloads that were published whole",
  );
  if (plan.withheld.length > 0) {
    io.out(`withheld: ${plan.withheld.length} lesson(s) not written; see the withheld section above for the next step`);
  }
  return {plan, written};
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  if (options.help) {
    process.stdout.write(USAGE);
    return;
  }
  const {db, FieldPath, target} = connect(options);
  await execute(options, {
    db,
    FieldPath,
    target,
    out: (text) => console.log(text),
    err: (text) => process.stderr.write(`${text}\n`),
  });
}

if (require.main === module) {
  main().catch((error) => {
    if (error instanceof UsageError) {
      console.error(`error: ${error.message}\n`);
      console.error(USAGE);
    } else {
      console.error(error && error.stack ? error.stack : error);
    }
    process.exit(1);
  });
}

module.exports = {
  USAGE,
  UsageError,
  BATCH_WRITES,
  BATCH_BYTES,
  parseArguments,
  checkProject,
  loadCredential,
  readCatalog,
  writeKeyDocuments,
  printReport,
  execute,
};
