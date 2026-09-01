---
title: 'E2.9 — What redaction would do to the live catalog, before it does it'
type: 'feature'
created: '2026-09-01'
status: 'done'
baseline_commit: 'd8f273eb'
review_loop_iteration: 1
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Every question in production today was written straight into `questions/{id}` by seed scripts and never went through publication, so switching redaction on at publication would change nothing that exists. Before anyone flips that switch, there is no way to know how many live questions can be split, how many would be refused and why, or what the key store would look like for the whole catalog.

**Approach:** A tool that walks the catalog and reports exactly what redaction would do — per lesson, per reason — and can write the key documents the way publication writes them today. It never rewrites a payload: clients cannot play a redacted question yet, and the step that rewrites payloads must produce both halves from one call, which this tool's key-only mode cannot promise.

## Boundaries & Constraints

**Always:** Dry run is the default and prints the full report; writing requires an explicit flag. The report is the deliverable — counts by lesson, by difficulty, by outcome, and every refusal with its reason and question id. Deciding what a question becomes is pure code that lives beside the redactor and is tested in the gated suite; the script is only the walk, the print and the write. Credentials come from the environment, never a path baked into the file.

**Ask First:** Anything that changes `questions/{id}.payload`, or writes to any collection other than the key store.

**Never:** Do not rewrite or delete a payload. Do not touch `functions/index.js` or publication. Do not change the redactor's or the key store's rules — this tool consumes them. Do not import `firebase-admin` into the pure part.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| Dry run | the catalog, no flag | a report and no writes at all |
| Write | the catalog, the write flag | one key document per lesson, identical to what publication would write for those questions, and the same report |
| Archived question | `archived: true` | skipped, counted as skipped, never keyed |
| Survey | a survey among the questions | counted as not applicable, no key, not a refusal |
| Unsplittable question | dangling correct id, malformed rows, unknown or legacy dialect | counted and listed by reason with its id; the lesson's other questions still get keys |
| Translated variant | `q1__ru` beside `q1` | each is its own question with its own key entry |
| Difficulty | none on the document; inside the payload, possibly absent or `""` | tallied from the payload; unreadable is its own bucket, never guessed |
| A lesson spanning pages | more questions than one query page | grouped whole before it is planned — a lesson is never split across two key documents |
| Re-run | run twice with the write flag | the second run replaces each lesson's document, same as a republish |

</frozen-after-approval>

## Code Map

- `functions/question-key-store.js` -- `questionKeyDocuments(questions, options)` → `{documents, refusals}`; per-lesson documents, lists not maps, `publicHalfRedacted: false`, refusal reasons exported as `REASON`, a size ceiling with `omitted`. Takes questions shaped `{id, lessonId, difficulty, payload}`; `difficulty: ""` is accepted (verified in E2.5). **The planner composes this; it must not reimplement any of it.**
- `functions/question-redaction.js` -- `STATUS` (`redacted` / `not-applicable` / `already-redacted` / `refused`) and `REFUSAL` reasons; the legacy `{"type":"single-choice",…,"correctIndex"}` dialect is refused as `unknown-type` by design, which the report must show as its own line since `scripts/seed-hierarchy.js:64-68` writes it.
- Public question document shape (`scripts/seed-bulk/verify-seeded-quest.js:263`, and publication at `index.js` `publicDocuments`): `id, lessonId, text, payload, language, languageLevel?, order, version, lastModifiedAt, archived`. **No `difficulty` field** — it lives inside `payload` and may be absent or `""` there (the redactor copies it verbatim).
- `scripts/backfill-catalogs.js` -- the house shape for a backfill: `firebase-admin`, `db.batch()`, plain `console.log`, `process.exit(1)` on error. Its credential is a hard-coded personal path (`/home/tpov/...`) that does not exist on this machine — do not copy that; use `GOOGLE_APPLICATION_CREDENTIALS` / `FIRESTORE_EMULATOR_HOST`.
- `scripts/seed-bulk/verify-seeded-quest.js:94-98` -- document-id range query precedent. A full walk needs `orderBy(FieldPath.documentId()).startAfter(last).limit(n)`; lessons span pages, so collect the whole catalog before planning.
- `scripts/package.json` -- its `test` runs two suites by hand and is in no gate. `functions/package.json:8-9` -- hand-maintained `lint`/`test` chains; `test` reaches `ciCheck`. The pure planner and its test go in `functions/` so they are gated; the script is not.
- Firestore batch limit 500 writes; one document per lesson, so chunk at 450 like `index.js` `commitOperations`. No atomicity is needed here — payloads are untouched, so a partial write leaves nothing inconsistent.
- `functions/question-key-store.test.js` -- the test style, and the fixtures for production-shaped questions.

## Tasks & Acceptance

**Execution:**
- [x] `functions/catalog-redaction-plan.js` -- new, pure. Given the catalog's question documents, returns the key documents to write (via the key store), every refusal, and a summary: per lesson and overall, counts of archived-skipped, not-applicable, keyed, refused-by-reason, difficulty buckets from the payload (EASY / HARD / unreadable), legacy-dialect count, translated-variant count.
- [x] `functions/catalog-redaction-plan.test.js` -- new; every Matrix row that needs no Firestore, built on the key store's own fixtures so the two cannot disagree.
- [x] `scripts/redact-existing-questions.js` -- new; walks `questions` with pagination, collects, calls the planner, prints the report; `--write-keys` writes the documents in chunks; exits non-zero on any error. Documents the env it needs at the top of the file.
- [x] `functions/package.json` -- add the planner to `lint` and its test to `test`, leaving other sessions' entries alone.

**Acceptance Criteria:**
- Given the catalog, when the tool runs without the flag, then nothing is written and the report names every refusal with a reason and an id.
- Given the flag, when it runs, then each lesson's key document equals what `questionKeyDocuments` produces for exactly that lesson's non-archived questions.
- Given a lesson whose questions span two query pages, when planned, then it yields one document.
- Given the same catalog, when run twice with the flag, then the second run's documents replace the first's.

## Verification

**Commands:**
- `cd functions && npm test` -- all suites pass, `catalog-redaction-plan` among them.
- `cd functions && npm run lint` -- passes.
- `node scripts/redact-existing-questions.js --help` -- prints usage and exits 0 without touching Firestore.

## Spec Change Log

- **Finding:** a lesson can hold a payload that is already one of the redactor's public shapes (`already-redacted`). The document the key store builds for that lesson carries no key for such a question — only a refusal — and writing it would replace the key stored beside the redacted payload with nothing, against the key store's own rule that such a key is left alone. Nothing in production is in that state today; a re-run after redaction is switched on would be.
  **Amended:** the planner returns those lessons in `withheld` (by lesson, path and question ids) instead of `documents`, and the script never writes them; the report lists them. No frozen content changed — the Matrix's "Write" row still holds for every lesson it can hold for.
  **Known-bad state avoided:** a stored, matched-generation key wiped by a key-only pass, which fails at scoring time rather than at the write.
  **KEEP:** the whole lesson is withheld, not the one question — the document is per lesson, and a partial replacement is a merge by another name.

- **Finding:** "built on the key store's own fixtures so the two cannot disagree" — the five payloads lived as module-local constants inside `question-key-store.test.js`, where a second suite could only copy them.
  **Amended:** they moved verbatim to `functions/_question-fixtures.js` (plus the legacy dialect string) and `question-key-store.test.js` now requires them; that suite is unchanged in behaviour (31 cases, still green). Same shape as `_seeded-random.js`, for the same reason.

- **Note:** the planner hands `payload` to the redactor as stored rather than through `normalizeQuestion`'s `stringValue`, so a document with no payload string is reported as `not-a-string` (what happened) rather than `malformed-json` (what the coercion would have made of it). No key is produced either way; only the reason differs. `id` and `lessonId` are coerced exactly as publication coerces them.

- **Verified beyond the gate:** against the Firestore emulator (JDK 24 at `/Library/Java/JavaVirtualMachines/zulu-24.jdk`, port 8765 because 8080 was held by another process): dry run wrote nothing and left `questions` byte-identical; `--write-keys --page-size 2` produced one document per lesson equal to `questionKeyDocuments` over the same rows, with a lesson spanning all six pages kept whole; a re-run after deleting one question and archiving another replaced that lesson's document whole.

## Review Loop 1

Three reviewers; thirteen patches, all applied. Where the patch changed what the tool promises, the
promise moved with it:

- **1, 2 — what "keyed" means, and how a lesson is withheld.** `keyed` now counts only keys in documents that will be written; keys in withheld lessons are `keysWithheld`, and the report prints "keys to write" and "keys withheld" as separate lines. A lesson is withheld on a pre-scan of its payloads for a `REDACTED_TYPE` discriminator, not on the refusal list — a full document records later refusals as a bare `omitted` count, which the earlier guard would have walked past. The rule cited is `STATUS.ALREADY_REDACTED` in `question-redaction.js`; publication shares the hazard and that is recorded separately.
- **3 — the ceiling is tested.** The key store suite's overflow fixture (8 heavy MultipleChoice + 4000 malformed rows) now runs through the planner: `refused` = recorded `document-full` + `omitted`, and `notApplicable` is asserted against a survey count taken from the fixture, not from the remainder. Mutation evidence: deleting the sentinel skip fails with `4001 !== 4000`; deleting the `omitted` branch fails with `1298 !== 4000`.
- **4 — legacy is bounded by its parent.** Counted only where the question's own refusal is `unknown-type`; printed unconditionally in the overall section; asserted `legacy ≤ unknown-type` overall and per lesson.
- **5 — refusals name the document.** Every considered row and every refusal carries `documentId`; the table prints lesson, document, question id, reason, detail, sorted by lesson then document; `index` is not printed.
- **6 — the script is testable.** `main()` is behind `require.main === module`; `parseArguments`, `checkProject`, `readCatalog`, `writeKeyDocuments`, `printReport`, `execute` are exported; `scripts/redact-existing-questions.test.js` (10 cases, fake Firestore in the style of `content-catalog-index.test.js`) is registered in `scripts/package.json` — in no gate, as recorded. A catalog of exactly N × page size now reports N pages (an empty page ends the walk and is not counted). Mutation evidence: `<` → `<=` fails on the row count; deleting the empty-page break fails with `3 !== 2`.
- **7 — the write cannot clobber a publication, or the wrong project.** Existing documents are read first and written with `update(…, {lastUpdateTime})`; absent ones with `create`; a failed commit re-reads its chunk, names the lessons that moved, reports how many documents had landed, and exits 1. `--write-keys` against a real project requires `--project <id>` equal to the credential's `project_id`. Mutation evidence: replacing the precondition write with a plain `set` fails the suite structurally (`'set' !== 'update'`), and run standalone against the concurrent-publication scenario the mutant overwrites the published document (`publicHalfRedacted` false, `keys` 0) where the real code rejects. The emulator run exercised both branches: `create` on the first write, `update` under precondition on the re-run.
- **8 — batches close by bytes as well as count** (450 writes, 8 MiB); `landed` is carried on the thrown error and printed.
- **9 — the report says what it wrote.** The `written:` line states `publicHalfRedacted: false`; withheld lessons print the next step (republish after the both-halves rewrite); documents with no keys are counted "of which with no keys" and marked in the table; one progress line per page goes to stderr; a missing or unreadable credential file is a usage error, not a stack.
- **10 — the key path coerces as publication does.** `difficulty` reaches the key store through `stringValue`, and the comparison fixture carries a document-level `difficulty`. Rule 3 in the module note now distinguishes the tally (never reads it) from the key path (passes it as publication would).
- **11 — `archived`.** Question-level only, as `questionRowFor` reads it; the report line, the file header and the module note say the quest's shelf is not consulted. The quest chain is deferred, as recorded.
- **12 — variants.** `translatedVariants` counts an id only when its base exists in the same lesson (archived or not); `variantsWithoutBase` reports the rest (`intro__part`, orphans).
- **13.** `Math.max(...)` replaced by a reduce; citations by symbol (`verifyQuestionDoc` pins nine fields, `publicDocuments` writes ten; the `question` literal in `seed-hierarchy.js`); the "once" claim dropped from `_question-fixtures.js`.

**Gate note at the time of this loop:** `cd functions && npm test` exits 1 on `economy-constants.test.js`, which fails against another session's uncommitted edit to `functions/economy-constants.js` (76 insertions, the charges work). Every suite in the chain before it passes, and `activity-kind.test.js` and `catalog-redaction-plan.test.js` (the two after it) pass when run directly; `npm run lint` and `cd scripts && npm test` exit 0.

## Suggested Review Order

- The three rules and the one thing held back, in the module note.
  [`catalog-redaction-plan.js:7`](../../functions/catalog-redaction-plan.js#L7)
- Surveys as the arithmetic remainder — the key store's contract, not a second parse.
  [`catalog-redaction-plan.js:250`](../../functions/catalog-redaction-plan.js#L250)
- Why `payload` is passed through and `id` is not.
  [`catalog-redaction-plan.js:75`](../../functions/catalog-redaction-plan.js#L75)
- The walk collects every page before planning; the test shows what per-page planning would have done.
  [`redact-existing-questions.js:111`](../../scripts/redact-existing-questions.js#L111)
- The write: plain `set`, key collection only, chunked at 450.
  [`redact-existing-questions.js:134`](../../scripts/redact-existing-questions.js#L134)

## Suggested Review Order

**What the report says, and why it can be trusted**

- Entry point: the planner composes the key store once over the whole catalog and adds only the tally — nothing about a question's fate is decided here.
  [`catalog-redaction-plan.js:200`](../../functions/catalog-redaction-plan.js#L200)

- A lesson holding an already-redacted payload is held back on a pre-scan of the payloads, not on the refusal list: a full document drops reasons into `omitted`, and a key-only write would have replaced a stored, matched-generation key with nothing.
  [`catalog-redaction-plan.js:4`](../../functions/catalog-redaction-plan.js#L4)

- The legacy dialect is counted only where the question's own refusal is `unknown-type`, so the line can never exceed its parent or fail to print.
  [`catalog-redaction-plan.js:70`](../../functions/catalog-redaction-plan.js#L70)

**What the script does to Firestore**

- The walk: `orderBy(documentId).startAfter`, terminating on a short page — the `<` that a reviewer flipped to `<=` with nothing failing is now pinned by a fake-Firestore test.
  [`redact-existing-questions.js:199`](../../scripts/redact-existing-questions.js#L199)

- Writes carry a `lastUpdateTime` precondition, so a publication landing mid-run is refused by name rather than overwritten from a stale read.
  [`redact-existing-questions.js:255`](../../scripts/redact-existing-questions.js#L255)

- A real-project write needs `--project` matching the credential — a key left in the shell must not turn a mistyped dry run into a production write.
  [`redact-existing-questions.js:125`](../../scripts/redact-existing-questions.js#L125)
