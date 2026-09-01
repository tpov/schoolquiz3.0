---
title: 'E2.5 — Publication starts writing the answer key'
type: 'feature'
created: '2026-09-01'
status: 'in-progress'
baseline_commit: 'a681181a'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `functions/question-redaction.js` can split a question from its answer, and Kotlin can read the public half, but nothing calls either. Publication still writes the full payload and stores no key, so the epic has no data to switch over to.

**Approach:** Publication starts producing the key and storing it where no client can read it. The published `payload` stays byte-identical — this step only creates the second half, so that turning redaction on later is a change of one write rather than a migration of two.

## Boundaries & Constraints

**Always:** The public question document must come out of this unchanged, field for field. A question that cannot be split must be visible, not silently skipped — a refusal means its answer is still public. The key must be unreadable by every client.

**Ask First:** Anything that changes what `questions/{id}` contains, or that makes a publish that succeeds today start failing.

**Never:** Do not redact the published payload. Do not add `difficulty` or `type` as fields on the public question document. Do not touch the client, the Kotlin schema, or the scoring path. No backfill of already-published questions.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|---|---|---|
| A lesson is published | questions of the four splittable types | one key document per lesson, holding a key per question id |
| Survey in the lesson | a survey among them | no key entry for it — it has no answer — and no refusal recorded either |
| A question cannot be split | dangling correct id, malformed rows, legacy dialect | no key entry, and the question id **and reason are recorded** in the same document |
| The payload | any question | byte-identical to what is published today |
| The public document | any question | the same ten fields as today — no `difficulty`, no `type` |
| Republish | a lesson published twice | keys are replaced, not merged with the previous generation |
| Batch cost | a submission of ~120 questions | publishes exactly as it does today — the write count per question must not rise |
| Client access | any client, signed in or not | reads and writes to the key collection are denied |

</frozen-after-approval>

## Code Map

- `functions/index.js:2883` `publicDocuments(request, now)` -- pure, returns a flat `path → data` map; the question branch is `:2944-2961`, writing exactly `id, lessonId, text, payload, language, languageLevel, order, version, lastModifiedAt, archived`. Adding a map entry here is the whole wiring — `writePublicHierarchyToBatch` (`:2877-2881`) does `batch.set(db.doc(path), clean(data), {merge: true})` for every entry. **Re-grep before editing: a parallel session has been moving this file all session.**
- `functions/index.js:2721` `requestWithPublishedQuestions` -- the questions actually published come from the **admin-review task documents**, not the request. So the payload to split is `task.questions[].payload`, the reviewer-edited text. `normalizeQuestion` (`:3930`) already surfaces `difficulty` and `type` on those objects, so `redact(q.payload, q.difficulty, {questionId: q.id})` has everything it needs at the `:2944` loop.
- `functions/index.js:2679` `db.batch()`, commit `:2717` -- one batch, no chunking. **A question already costs 4 writes** (public doc + two sync-change stubs + the admin question doc), so Firestore's 500-write cap already breaks publication somewhere near 125 questions, and nothing validates question count. A key document *per question* would make it 5 and drop that ceiling to ~100 — breaking submissions that work today. **One key document per lesson** keeps the cost per question unchanged, and matches how the server already reads questions (`where("lessonId","==",lessonId)`, `:292`).
- `functions/index.js:1442-1450` `parseQuestionPayload` -- merges `{...fallback, ...parsed}` where `fallback.difficulty` is `stringValue(data.difficulty)`. That field does not exist on public docs today, so the fallback is always `""`, which `lessonAllocatedSeconds` (`lesson-reward.js:199`) coerces to `EASY`. **Adding a real `difficulty` field would flip every question whose payload lacks one from EASY to its true difficulty, moving lesson rewards and unlock prices.** This is why the public document must stay as it is.
- `functions/index.js:4605` `clean()` -- recursively strips `undefined` and walks into plain objects. A key's nested `idMap` survives, but Firestore rejects field names containing `/`, `.` or wrapped in `__`; the redacted ids are `ri-N` / `rc-N`, and blank ids come from the author.
- `functions/question-redaction.js` -- `redact(payloadJson, difficulty, {random, questionId})` → `{status, publicPayload, key, reason}`. `status` is `redacted` / `not-applicable` (Survey) / `already-redacted` / `refused` with a reason. Only `key` is used here; `publicPayload` is discarded this step.
- `functions/index.js` has **zero** `console.*` and no logger import — there is no existing way to surface a refusal, which is why it goes into the document.
- `firestore.rules:133` -- `match /questions/{questionId} { allow read: if true; allow write: if isAdmin(); }`. House-style deny: `nickname_claims` `:37-40`, `configs` `:48-51`, `mutation_keys` `:42-46` — all `allow read, write: if false;`. The file is also being edited concurrently.
- `functions/lesson-reward.js` -- the model for a pure, `firebase-admin`-free module; `index.js` itself has no test, so the logic must live outside it to be testable.

## Tasks & Acceptance

**Execution:**
- [x] `functions/question-key-store.js` -- new; pure, no `firebase-admin`. Given a lesson id and its questions, returns the single key document: the key for every question that split, and the reason for every one that refused. Both are **lists, not maps keyed by question id** — `writePublicHierarchyToBatch` writes `{merge: true}` and merge recurses into maps, so a map would union with the previous generation and a republish would stop being a replacement. Firestore replaces an array field whole. Rejects a key whose field names Firestore cannot store, rather than emitting a document that fails to write.
- [x] `functions/question-key-store.test.js` -- new; every Matrix row that does not require Firestore, including the Survey and refusal cases and the field-name check.
- [x] `functions/index.js` -- in the `publicDocuments` question loop, accumulate keys per lesson and emit one key-document entry per lesson. Nothing else in that function changes. Re-read the surrounding lines immediately before editing.
- [x] `firestore.rules` -- deny the key collection outright, in the house style, with a comment saying the denial is deliberate.
- [x] `functions/package.json` -- append the new module to `lint` and its test to `test`, leaving the parallel session's entries alone.
- [x] `functions/question-redaction.js` -- export the refusal reason set (`REFUSAL`) so callers name a reason instead of copying a literal, and state the one-`redact`-call invariant in `redact`'s own docs. Behaviour-preserving: the string values are unchanged and both existing suites stay green.
- [x] `scripts/rules-emulator-test.js` -- a `question_keys/les-1` fixture plus deny cases for player, guest and admin. Firestore denies by default, so the behavioural cases catch a *widening* of the rule, not its deletion; the explicit block is pinned as text so deleting it fails too.

**Acceptance Criteria:**
- Given a published lesson, when the batch is built, then the public question documents are byte-identical to today's and exactly one additional document is written for the whole lesson.
- Given a question that cannot be split, when the lesson is published, then its id and the reason appear in the key document — a refusal is never silent, because it means the answer is still public.
- Given a lesson republished, when the key document is written, then it replaces the previous generation rather than merging with it.
- Given a submission near the size that publishes successfully today, when it is published, then the number of writes per question is unchanged.

## Verification

**Commands:**
- `cd functions && npm test` -- all suites pass, `question-key-store` among them.
- `cd functions && npm run lint` -- passes with the new module listed.
- `git diff -- functions/index.js` -- touches only the `publicDocuments` question loop and the require block.

## Spec Change Log

- **Finding:** the Tasks section asked for the key document to hold "a map of question id -> key". That cannot satisfy the Matrix row requiring a republish to replace rather than merge: publication writes every document with `{merge: true}`, and Firestore's merge recurses into maps, so a shrinking key set would leave the previous generation's entries behind. Arrays are replaced whole.
  **Amended:** the task now specifies lists and says why. No frozen content changed — the Matrix row was already right; the task's wording contradicted it.
  **Known-bad state avoided:** a republished lesson carrying answer keys for questions it no longer has, with nothing able to clear them.
  **KEEP:** one key document per lesson rather than per question (it keeps the per-question write count unchanged, so no publication that succeeds today starts failing, and it matches how the server already reads questions); refusing rather than emitting a document Firestore would reject; and keeping `publicDocuments` a pure, IO-free function by calling the pure redactor inline.
