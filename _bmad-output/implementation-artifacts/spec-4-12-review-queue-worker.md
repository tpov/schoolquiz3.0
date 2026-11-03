---
title: '4.12 review-queue-worker — VPS worker drains the review queue (AD-32 machinery)'
type: 'feature'
created: '2026-10-26'
status: 'done'
route: 'full'
route_source: 'auto'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
baseline_commit: 'cb40cd0a80aa8289974b77b5843fe1b2a70eeb88'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A submitted quest sits `UNDER_REVIEW` forever: the pipeline that should judge it (AD-32, sprint-change-proposal story 4.12) does not exist yet. The checks themselves are stories 4.13/4.14; what is missing first is the durable machinery that drains the queue, feeds lessons to check stages one at a time, records immutable verdicts, and never lets a model failure become a guessed verdict.

**Approach:** A standalone VPS worker process (no Gradle, no scheduled function) modeled on `arena-season-worker` (lease, checkpoint, bounded steps): it claims the oldest accepted submission, walks its lessons one by one with the quest title and category path as context, runs pluggable stages through a schema linter (invalid output returns a named error, at most three retries, then REVIEW_DELAYED), and writes immutable per-(submission, lesson, stage, attempt) verdict records. The GLM API key comes from the VPS environment, never from Git.

## Boundaries & Constraints

**Always:**
- One quest at a time across the whole fleet: a lease document in a server-private state collection, exactly like the arena season sweep; a crashed worker's lease expires and another run resumes without double work.
- Job identity is (submissionId, lessonId, stage, attempt); the verdict document id is derived from that identity and written with `create` only — a repeat of the same job never produces a second verdict.
- Verdict records are immutable and bind revision, stage, model, promptVersion, sources, rationale, attempts, and per-question PASS/FAIL with a reason.
- Lesson text travels as data inside explicit delimiters; the model reply is judged by a linter (pure code), never trusted by shape.
- Model failure (transport, 5xx, unparsable after retries) ends in pipeline state REVIEW_DELAYED — never REJECTED-by-quality, never a fabricated verdict.
- New collections (`review_pipeline`, `review_verdicts`, worker state) are server-only: closed by `allow read, write: if false` rules with a rules probe.
- The stage interface is injectable; 4.12 ships scripted fake stages in tests only — no real prompt content, that is 4.13/4.14.

**Never:**
- No model prompt engineering in this story; no translation step (4.15); no publication/author-outcome changes (4.16); no fee changes (4.18).
- No client code and no client-visible names yet (sentinels come with the client stories).
- The worker never reads credentials from files under the repo; key + endpoint + model name come from env (`REVIEW_MODEL_API_KEY`, `REVIEW_MODEL_API_URL`, `REVIEW_MODEL_NAME`), and a missing key is a loud startup refusal.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy drain | Submission UNDER_REVIEW, no pipeline doc | Worker claims it (lease), creates pipeline doc, runs each lesson through registered stages in order, records verdicts, finishes with pipeline status QUEST_REVIEWED | No error expected |
| Stage output valid | Linter accepts raw output | One verdict record per (lesson, stage, attempt), pipeline stage state ADVANCED | No error expected |
| Linter rejects ≤3 times | Raw output fails schema with named error | Same attempt retried with the named error appended (attempts 1..3), then lesson state REVIEW_DELAYED | No guessed verdict recorded; DELAYED is visible in pipeline state |
| Model transport failure | Client throws REVIEW_MODEL_UNAVAILABLE | Lesson stays pending (retryable), quest remains queued; sweep reports the failure without crashing | Bounded retry on later sweeps |
| Worker crash mid-lesson | Lease expires while lesson in progress | Completed verdicts survive (immutable); the in-flight stage attempt restarts on a later sweep under the same identity only if no verdict exists for it | No duplicate verdicts |
| Concurrent workers | Second sweep while lease held | BUSY result, no claim, no writes | Lease expiry governs |
| Empty queue | No submissions to review | Sweep returns IDLE with zero attempts | No error expected |
| Missing env key | Runner started without REVIEW_MODEL_API_KEY | Process refuses to start with a named error | Loud, immediate |
| Unparsable submission doc | Malformed request fields | Worker skips the submission, marks pipeline FAILED with reason, never blocks the queue | Named, logged (hashed), queue moves on |

</frozen-after-approval>

## Code Map

- `functions/arena-season-worker.js` — the lease/checkpoint/bounded-step pattern to copy (state doc, `acquire`/`checkpoint`/`release`, no client authority, hashed-only logging).
- `functions/index.js:5774` (`processArenaRequest`) — writes `quest_review_requests/{submissionId}` with status `UNDER_REVIEW`, `draft` (id, catalogId, title), `lessons[]`, `questions[]`, `localRevision`; the queue the worker drains. `:9024` `adminLessonPath` naming conventions.
- `functions/review-fee-quote.js` / `functions/review-fee.js` — the pure-module + thin-callable + dedicated `*.test.js` layout this story follows.
- `functions/_firestore-stub.js` — in-memory admin stand-in that runs `index.js` end to end; worker suites compose it with real module code (queries: one equality `where` per collection — keep worker queries inside that contract or use doc gets).
- `scripts/arena-e2e-evidence.js:13` + `scripts/*-private-rules.test.js` — the VPS process shape (admin init, env asserts) and the rules-probe pattern (host `schoolquiz-mvp-firebase`, demo project).
- `firestore.rules:232` — the `quest_review_requests` `allow read, write: if false` precedent to copy for the new collections.
- Do not touch: fee charge/settle, publication (`publishSubmissionIfReady`), the client, the stage prompts.

## Tasks & Acceptance

**Execution:**
- [ ] `functions/review-model-client.js` — GLM client: env-driven endpoint/key/model, chat-completions POST, injectable transport; wraps lesson payloads as data between delimiters; named failures (`REVIEW_MODEL_UNAVAILABLE`, `REVIEW_MODEL_BAD_REPLY`); key never logged.
- [ ] `functions/review-pipeline.js` — pure pipeline machine: stage ordering per lesson, attempt ledger (≤3), linter-retry loop with named errors, stage/lesson/quest state transitions incl. REVIEW_DELAYED and QUEST_REVIEWED, verdict-record assembly from (identity, model, promptVersion, parsed verdict).
- [ ] `functions/review-queue-worker.js` — `createReviewQueueWorker({db, stages, modelClient, nowMs, log, ...})`: lease in `review_worker_state/sweep`, claim oldest eligible submission, per-sweep bounded steps (one lesson-stage per step), verdict writes via deterministic-id `create`, pipeline doc as the resumable checkpoint; sweep result statuses like the arena worker's.
- [ ] `functions/review-queue-worker.test.js` + `functions/review-pipeline.test.js` + `functions/review-model-client.test.js` — cover the matrix with the firestore stub and scripted stages: valid flow, linter retry → DELAYED, crash/lease expiry resumption without duplicate verdicts, BUSY, IDLE, missing key, unparsable submission.
- [ ] `scripts/review-queue-worker-run.js` — VPS runner: admin init (ADC), env asserts, sweep loop with sleep, JSON log lines with hashed ids only.
- [ ] `firestore.rules` — close `review_pipeline`, `review_verdicts`, `review_worker_state` (server-only) + `scripts/review-pipeline-private-rules.test.js` probe (auth user read/write denied on all three).

**Acceptance Criteria:**
- Given an accepted submission, when the worker sweeps, then every lesson passes through the registered stages in order and the pipeline ends QUEST_REVIEWED with one immutable verdict per (lesson, stage, attempt).
- Given a model reply that fails its schema three times, when the linter runs, then the lesson lands REVIEW_DELAYED with zero verdict records for that stage and the named errors preserved in the pipeline state.
- Given a crashed run whose lease expired, when a new sweep starts, then finished verdicts are untouched, the interrupted stage re-runs only if it has no verdict, and no stage runs twice with verdicts.

## Implementation Notes

- Workflow deviation, sanctioned by the run mandate: autonomous wave under sprint-change-proposal-2026-10-26 §5 step 5; spec goes straight to `ready-for-dev`.
- The stage registry in 4.12 ships empty of real stages — `createReviewQueueWorker` refuses a quest whose stages are not all registered (pipeline state STAGE_MISSING, loud), so 4.13/4.14 plug in without touching the worker.

## Spec Change Log

- 2026-10-27, implementation loopback: judging architecture sharpened during tests — the stage skill owns prompts + lint, while the model call itself is made by the shared `review-model-client` (transport + key in one place). `requireStageSkill` validates the prompt/lint contract, not a judge. The known-bad state avoided: skills each carrying their own transport and key handling.
- 2026-10-27, implementation loopback: the sweep gained an inner per-quest loop (`processQuest`): a sweep runs one quest to its terminal state or until the sweep's stage-call budget is exhausted, re-reading the pipeline document between steps. The first draft advanced one job per sweep, which would leave quests half-checked for many sweeps. Between-step re-reads make crash recovery read truth from the document, not from memory.
- 2026-10-27, verification notes: gates as run — functions `npm test` 132/133 (the one red, `mvp-isolation.test.js`, is the same pre-existing attribution as 4.11; `arena-lifecycle-views.test.js` flaked once under `npm test` and passed standalone and in a full rerun — recorded for the owner, untouched surface); rules probe `scripts/review-pipeline-private-rules.test.js` PASS (27 denies across author/appAdmin/anonymous × GET/PATCH/DELETE on all three collections + a live-ruleset positive control: catalogs read 200 while review paths 403; the running demo emulator still carries the 2026-10-04 ruleset, whose default-deny already covers the new collections — the explicit matches land at the next owner-gated rules deploy); runner smoke: `node scripts/review-queue-worker-run.js` refuses loudly without `REVIEW_MODEL_API_URL/KEY/NAME` (`REVIEW_MODEL_ENV_MISSING`) and `--once` sweep returns `STAGE_MISSING` until 4.13/4.14 register skills.

## Review Triage Log

## Design Notes

- Verdict id: `review_verdicts/{submissionId}_{lessonId}_{stage}_{attempt}` — deterministic, so idempotency is the document name, not bookkeeping.
- Why a standalone process and not `onSchedule`: a judging call with web search takes minutes (AD-32); the arena worker's own 1-minute scheduler would stack instances. The VPS loop owns its own cadence.
- The worker composes `db` from admin SDK in the runner; tests inject the firestore stub through the same factory — no module ever touches `firebase-admin` directly.

## Verification

**Commands:**
- `ssh rnsrv 'docker exec schoolquiz-functions-emu sh -c "cd /repo/functions && node review-pipeline.test.js && node review-model-client.test.js && node review-queue-worker.test.js"'` -- three suites green.
- `ssh rnsrv 'docker exec schoolquiz-functions-emu sh -c "cd /repo/functions && npm test"'` -- full functions suite green (only the attributed pre-existing `mvp-isolation` red carried, see spec-4-11).
- Rules probe under the emulator: same invocation pattern as `scripts/*-private-rules.test.js` (env: GCLOUD_PROJECT=demo-schoolquiz-mvp, FIRESTORE_EMULATOR_HOST=schoolquiz-mvp-firebase:8080) -- all denies hold.
